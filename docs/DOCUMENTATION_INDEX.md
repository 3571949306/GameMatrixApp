# GameMatrixApp Documentation Index

Last updated: 2026-05-21

## Canonical docs

- `README.md`
  - External-facing overview.
  - Keep this focused on project introduction, local build, and the shortest usable release entrypoints.

- `PROJECT_CONTEXT.md`
  - Maintainer context and repo operating constraints.
  - Use this as the default handoff/start-here document for future repo work.

- `CODE_WIKI.md`
  - Code structure and module-level technical reference.
  - Do not treat it as the source of truth for release state or temporary rollout notes.

- `项目改进建议书.md`
  - Ongoing maintenance and governance document.
  - Record documentation cleanup decisions and remaining drift here.

- `CHANGELOG.md`
  - Version history and user-visible changes.

- `docs/PUBLISH_GUIDE.md`
  - Single publishing guide.

- `docs/LOCAL_GITHUB_NETWORK.md`
  - Local GitHub connectivity and proxy recovery notes for this Windows machine.

## Archived docs

The following documents were moved out of the repo root because they were historical, duplicated other guides, or had mixed old/new release content:

- `docs/archive/context/AI_CONTEXT.md`
- `docs/archive/publish/PUBLISH_SYSTEM_OVERVIEW.md`
- `docs/archive/publish/UPLOAD_INSTRUCTIONS.md`
- `docs/archive/publish/AUTO_PUBLISH_README.md`
- `docs/archive/releases/RELEASE_COMPLETE.md`
- `docs/archive/releases/RELEASE_STATUS.md`
- `docs/archive/releases/RELEASE_SUMMARY_1.3.18.md`
- `docs/archive/network/联机架构改造说明.md`
- `docs/archive/network/WEBSOCKET_MIGRATION.md`
- `docs/releases/RELEASE_NOTES_v1.3.27.md`

## Maintenance rules

1. One document should have one job.
2. Release snapshots belong in `CHANGELOG.md` or versioned release notes, not in long-lived context docs.
3. `README.md`, `PROJECT_CONTEXT.md`, and `CODE_WIKI.md` must not each carry separate version tables that drift independently.
4. Historical migration reports should live under `docs/archive/`, not in the repo root.
5. When code structure changes, update `PROJECT_CONTEXT.md` and `CODE_WIKI.md` together; when release state changes, update `CHANGELOG.md` and versioned release notes.
