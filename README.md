# Tree-lint JetBrains IDE Plugin

A plugin for JetBrains IDEs (WebStorm and others) that runs
[`@redwestdev/tree-lint`](https://github.com/redwestdev/tree-lint) and shows
project structure violations right inside the IDE.

## What it does

- **Three lint modes** (radio buttons in **Settings → Languages & Frameworks →
  Tree-lint**, like the Biome/ESLint plugins):
  - `Disabled` — the plugin is off, all highlighting is cleared;
  - `Automatic` — everything is detected automatically: the root config is
    searched from the project folder upwards
    (`tree-lint.config.{ts,js,mjs,mts,json,yaml,yml}`), Node.js comes from
    `PATH`, and the CLI is resolved as
    `node_modules/@redwestdev/tree-lint/dist/cli.js` (including monorepos) →
    `node_modules/.bin/tree-lint` → `tree-lint` from `PATH`. Scans run: when
    the project opens, on file save (if the checkbox is on), on file
    create/rename/delete, and when the config changes (always);
  - `Manual` — scans run only on demand via **Tools → Tree-lint: Scan
    Project** (or the **Scan project** button in the tool window). This mode
    shows the manual fields: `tree-lint` executable, Node.js executable,
    configuration file, and scan timeout.
- **Checkboxes**: "Scan project on file save" and "Show the Tree-lint tool
  window when violations are found".
- **Project view highlighting**: files and directories with violations — and
  **every parent folder up to the project root** — get a wavy underline in
  the severity color. Parents use the most severe descendant severity, and
  their tooltip shows the number of violations inside.
- **Tree-lint tool window**: a table of violations (Severity / Message /
  Path) with a status bar that shows the detected config name. Double-click
  opens a file; for a directory it selects it in the project view.

## Requirements

- JDK 21+ to build (Gradle downloads the toolchain automatically via Foojay).
- Node.js 18+ on `PATH` (or an explicit path in the Manual mode settings).
- A `tree-lint.config.*` in the project (created via `tree-lint init`); scans
  are not possible without it and the plugin shows a message in that case.
- A `tree-lint` version that supports `--json` (see the contract below).

## CLI contract

The plugin does not parse colored output. It runs:

```bash
tree-lint scan <project> --json --no-fail-on-error
```

and expects JSON on stdout:

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

`--no-fail-on-error` keeps the exit code at zero when violations exist so the
report is still parsed; the plugin decides how to present errors. If stdout is
empty, the plugin shows stderr and the exit code.

Severity comes from the tree-lint config (`type` fields): `error` → red,
`warning` → yellow, custom types → blue.

## Building

```bash
./gradlew buildPlugin
```

The resulting archive is
`build/distributions/tree-lint-intellij-plugin-0.1.0.zip`.

By default the build downloads WebStorm `platformVersion` from
`gradle.properties`. To build against an installed IDE instead:

```bash
./gradlew buildPlugin -PlocalPlatformPath=/Applications/WebStorm.app
```

## Running in a sandbox

```bash
./gradlew runIde -PlocalPlatformPath=/Applications/WebStorm.app
```

An isolated WebStorm instance with the plugin installed opens. Open a project
with a `tree-lint.config.*` and save any file — the project view and the
Tree-lint panel update automatically.

## Installing manually

1. Build the archive (`./gradlew buildPlugin`).
2. **Settings → Plugins → ⚙ → Install Plugin from Disk…**
3. Pick `build/distributions/tree-lint-intellij-plugin-0.1.0.zip`.

## Testing

```bash
./gradlew test
```

Unit tests cover JSON report parsing, severity mapping, config discovery,
CLI command resolution, and path/ancestor highlighting logic.

## Structure

```text
src/main/kotlin/dev/redwest/treelint/
├── action/      # manual scan action (Tools → Tree-lint: Scan Project)
├── model/       # JSON report DTOs + severity logic
├── runner/      # CLI resolution, config discovery, report parsing, process run
├── service/     # per-project report cache + scan scheduler (save/config)
├── settings/    # settings state + UI
└── ui/          # tool window + project view decorator
```

## Known limitations

- Highlighting lives in the project view only; code-level highlighting will
  arrive together with import analysis.
- No LSP: tree-lint is a file-tree CLI linter and ships no language server.

## Roadmap

- Code highlighting in the editor (for import analysis).
- Quick fixes (e.g. moving a file to the expected directory).
- Autocompletion and validation for `tree-lint.config.*` via JSON Schema.
- JetBrains Marketplace publishing (`publishPlugin`).
