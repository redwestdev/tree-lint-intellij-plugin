# Tree-lint for WebStorm

Плагин для JetBrains IDE (WebStorm), который запускает
[`@redwestdev/tree-lint`](https://github.com/redwestdev/tree-lint) и показывает
нарушения структуры проекта прямо в IDE.

## Что делает

- **Три режима линта** (радиокнопки в настройках, как у Biome/ESLint):
  - `Disabled` — плагин выключен, подсветка снимается;
  - `Automatic` — всё определяется автоматически: корневой конфиг ищется
    от папки проекта вверх (`tree-lint.config.{ts,js,mjs,mts,json,yaml,yml}`),
    Node.js — из PATH, CLI — `node_modules/@redwestdev/tree-lint/dist/cli.js`
    (включая монорепо) → `node_modules/.bin/tree-lint` → `tree-lint` из PATH.
    Скан запускается: при открытии проекта, при сохранении файла (если
    включён чекбокс), при создании/переименовании/удалении файлов и при
    изменении конфига (всегда);
  - `Manual` — скан только по команде **Tools → Tree-lint: Scan Project**
    (или кнопкой «Scan project» в панели); в этом режиме доступны поля для
    ручной настройки: путь к `tree-lint`, путь к Node.js, путь к конфигу,
    таймаут.
- **Чекбоксы**: «Scan project on file save» и «Show the Tree-lint tool window
  when violations are found».
- **Подсветка в дереве проекта**: волнистое подчёркивание в цвет severity у
  файлов/директорий с нарушениями **и у всех родительских папок вплоть до
  корня** (severity — максимальный среди потомков, в тултипе — число
  нарушений внутри).
- **Tool window «Tree-lint»** — таблица нарушений (Severity / Message / Path)
  со статусом и именем найденного конфига; двойной клик открывает файл,
  для директории — выделяет её в дереве проекта.

## Требования

- JDK 21+ для сборки (Gradle сам скачает toolchain 21 через Foojay, если его нет).
- Node.js 18+ в `PATH` (или явно указанный путь в настройках).
- Конфиг `tree-lint.config.*` в проекте (создаётся командой `tree-lint init`);
  без конфига скан невозможен — плагин покажет сообщение об этом.
- Версия `tree-lint` с поддержкой `--json` (см. контракт ниже).

## Контракт с CLI

Плагин не парсит цветной вывод. Он запускает:

```bash
tree-lint scan <project> --json --no-fail-on-error
```

и ожидает JSON в stdout:

```json
{
  "generatedAt": "2026-01-01T00:00:00.000Z",
  "projectRoot": "/project",
  "errors": 1,
  "warnings": 0,
  "violations": [
    {
      "type": "error",
      "path": "/project/src/components/Ui",
      "message": "Name length is outside the allowed character limits"
    }
  ]
}
```

`--no-fail-on-error` нужен, чтобы наличие ошибок не мешало разбору отчёта:
плагин сам решает, как показывать ошибки. Если JSON пустой, плагин показывает
stderr и код выхода.

Severity нарушений задаётся самим tree-lint (поля `type` в конфиге):
`error` → красный, `warning` → жёлтый, остальные типы → синий.

## Сборка

```bash
./gradlew buildPlugin
```

Готовый архив: `build/distributions/tree-lint-ide-plugin-0.1.0.zip`.

По умолчанию сборка скачивает WebStorm `platformVersion` из `gradle.properties`.
Чтобы использовать установленную IDE:

```bash
./gradlew buildPlugin -PlocalPlatformPath=/Applications/WebStorm.app
```

## Запуск в песочнице

```bash
./gradlew runIde -PlocalPlatformPath=/Applications/WebStorm.app
```

Откроется изолированный экземпляр WebStorm с установленным плагином. Откройте
проект с `tree-lint.config.*` и сохраните любой файл — дерево проекта и панель
«Tree-lint» обновятся.

## Установка в WebStorm вручную

1. Соберите архив (`./gradlew buildPlugin`).
2. **Settings → Plugins → ⚙ → Install Plugin from Disk…**
3. Выберите `build/distributions/tree-lint-ide-plugin-0.1.0.zip`.

## Структура

```text
src/main/kotlin/dev/redwest/treelint/
├── model/       # DTO JSON-отчёта + severity
├── runner/      # запуск CLI и разбор отчёта
├── service/     # кэш отчёта на проект + планировщик сканов (save/config)
├── settings/    # настройки + UI
└── ui/          # tool window + подсветка дерева проекта
```

## Известные ограничения

- Подсветка только в дереве проекта; подсветка кода появится вместе с
  анализом импортов.
- LSP не используется: tree-lint — CLI-линтер дерева файлов, LSP-сервера у
  пакета нет.

## Следующие шаги

- Триггеры на создание/переименование файлов.
- Подсветка кода в редакторе (для анализа импортов).
- Быстрые исправления (например, перемещение файла в ожидаемую директорию).
- Автодополнение и валидация `tree-lint.config.*` через JSON Schema.
- Публикация в JetBrains Marketplace (`publishPlugin`).

