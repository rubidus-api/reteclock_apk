# Release Notes

## 0.5.0

- Replace new-project `TODO.md` guidance with `BACKLOGS.md` plus detailed backlog folders.
- Add root `HANDOFF.md` and optional handoff archive folders for compact session recovery.
- Add `boot/MIGRATION_PROMPT.md` and `boot/scripts/migrate-existing-project.sh` for conservative existing-project alignment.
- Add checks for backlog, handoff, and migration behavior.

## 0.4.2

- Add rootsator-level `scripts/project-check.sh` as the standard full verification entrypoint.
- Tighten project-check private-pattern scanning so the scanner does not flag its own detection expressions or explanatory docs.

## 0.4.1

- Relax `lint-init.sh`: accept inline `Label: value` and bullet/plain values, not only fenced ```text blocks. Still flags missing/empty fields and leftover placeholders. Drop ineffective word-based secret matching and keep only high-signal private-value checks.

## 0.4.0

- Make `boot/manifest.json` the authoritative payload list and stop pre-creating `docs/agents/*` and `scripts/*` in BOOT, so copied modules and scripts are never left empty.
- Contain secrets in ignored directories (`*_private/`, `*_internal/`, `secrets/`) instead of broad filename globs; keep only the `.env` family in place and keep `.env.example` tracked. Operating-doc ignores are root-anchored.
- Add the `Operating docs tracking: local | tracked` INIT toggle and document the durable-vs-local doc policy in `.gitignore`.
- Rename the local work history file from `CHANGELOGS.md` to `WORKLOG.md` to remove the one-letter clash with the public `CHANGELOG.md`.
- Remove Windows support from scripts; scripts target Linux/POSIX shells only.

## 0.3.0

- Add reusable target-project script templates for tool checks, project checks, active plan creation, plan archiving, and changelog entries.
- Add public `CHANGELOG.md` generation guidance based on Keep a Changelog.
- Document Linux/POSIX-only script behavior.

## 0.2.0

- Add `boot/manifest.json` to describe bootstrap payload files, target copies, generated directories, and generated files.
- Add `boot/scripts/lint-init.sh` for required `INIT.md` field checks and unsafe value checks.
- Add `boot/scripts/verify-bootstrap.sh` for bootstrap package dry-run validation.
- Add `boot/gitignore_template` so target projects receive private-state and generated-output ignore rules during bootstrap.

## 0.1.0

- Provide temporary root launcher, one-time bootstrap procedure, final compact AGENTS template, and modular agent rule files.
