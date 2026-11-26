# Changelog

## [Unreleased]

### Changed

- Scan command updated to the `--report` flag and the reshaped JSON report
  (`grouped` by path) shipped in `@redwestdev/tree-lint` 0.0.6. The plugin now
  requires tree-lint 0.0.6 or newer.
- CLI failures in report mode are read from the stderr `{"error": "..."}`
  payload instead of showing the raw JSON.

## [0.1.0]

### Added

- Automatic and manual scan modes (Settings → Languages & Frameworks → Tree-lint).
- Automatic discovery of `tree-lint.config.*`, Node.js and the `tree-lint` CLI.
- Scans on project open, file save, file create/rename/delete and config changes.
- Project view highlighting with severity colors and violation counts on parent folders.
- Tree-lint tool window with a violation table and a manual "Scan project" action.
- `Tools → Tree-lint: Scan Project` action and scan timeout setting.
