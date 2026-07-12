# Project State

Current accepted behavior lives in `SPEC.md`.

Current accepted requirements live in `REQUIREMENTS.md`.

Use:

- `README.md` for the public GitHub-facing introduction page.
- `AGENTS.md` for permanent agent routing and operating rules.
- `CONTEXT.md` for current state, blockers, and next steps.
- `HANDOFF.md` for current session-transition and resume packets.
- `DECISIONS.md` for one append-only accepted decision log.
- `LESSONS.md` for lessons, major bugs, hazards, and prohibited actions.
- `CHECKLIST.md` for concise repeatable checks derived from current risk.
- `MEMORY.md` for stable facts, preferences, and durable project knowledge.
- `SPEC.md` for current accepted behavior and design.
- `REQUIREMENTS.md` for current accepted requirements.
- `docs/tests/test-index.md` for the compact authoritative TDD catalog.
- `TESTS.md` for the active local TDD cycle.
- `BACKLOGS.md` for actionable pending work and active focus.
- `docs/backlogs/backlog-index.md` for detailed backlog item lookup.
- `docs/requirements/requirements-log.md` for requirement history.
- `docs/requirements/conflicts.md` for contradictions and precedence.
- `CHANGELOG.md` for public release history.
- `WORKLOG.md` for private/local AI work history.

Read history files only when history, conflict analysis, or prior work context is relevant.

## Root File Operations

| File | Update when | Do not use for |
|---|---|---|
| `README.md` | Public purpose, install, usage, public API, project structure, license, or visible behavior changes. | Private notes, local paths, internal run logs, secrets. |
| `CHANGELOG.md` | Public release entries or notable user-visible changes need recording. Keep `Unreleased` and Keep a Changelog sections current. | Private/local work traces, raw task logs, internal-only implementation notes, secrets. |
| `AGENTS.md` | Agent routing, operating rules, required structure, or default context policy changes. | Task notes or project history. |
| `CONTEXT.md` | Bootstrap completion, significant work, blockers, current state, or next-step handoff changes. | Accepted requirements, long history, detailed test cases. |
| `HANDOFF.md` | Session pause, model switch, interruption, unfinished goal, or explicit handoff. | Long work history, accepted project truth, backlogs, or private credentials. |
| `MEMORY.md` | Stable reusable facts, user preferences, durable non-decision knowledge, or project patterns are discovered. | Accepted decisions, chronological work logs, requirement history, temporary plans. |
| `SPEC.md` | Accepted behavior, architecture, workflow contract, design, public behavior, or project structure changes. | User discussion history, rejected alternatives, unaccepted ideas, chronological requirement changes. |
| `REQUIREMENTS.md` | Current accepted requirements, constraints, acceptance criteria, or success criteria change. | Chronological requirement changes, contradictions, design rationale, implementation notes. |
| `DECISIONS.md` | A stable design/workflow choice is accepted or superseded. | Secrets, private-only decisions, routine facts, lessons, backlogs. |
| `LESSONS.md` | A severe bug, hazard, prohibited action, or reusable lesson is found. | General memory, accepted decisions, public release notes. |
| `TESTS.md` | The active task's red/green/refactor status or verification command changes. | Full test catalog or detailed case specs. |
| `BACKLOGS.md` | New actionable pending work appears, priorities change, or work moves between active/ready/later. | Decisions, requirements, accepted truth, or broad unactionable wishlists. |
| `CHECKLIST.md` | A short repeatable check is promoted from current risk or `LESSONS.md`. | Full lessons or task plans. |
| `WORKLOG.md` | Notable local AI work completes or a useful private work trace is needed. | Public changelog entries or current state. |

## Backlog Operations

Use `BACKLOGS.md` as the compact current backlog and active-work queue.

Recommended sections:

```text
## Active Focus
## Ready
## Later
## Blocked
```

Keep each item short and actionable. If an item needs acceptance criteria, dependencies, investigation notes, or a multi-step implementation plan, create a detail file under `docs/backlogs/items/` and add a one-line pointer in `docs/backlogs/backlog-index.md`.

Do not use `TODO.md` for new work. If an older project has `TODO.md`, migrate actionable entries to `BACKLOGS.md` and keep `TODO.md` only as a temporary legacy note until it is removed or ignored.

## Handoff Operations

Use root `HANDOFF.md` as the current resume packet.

Update it before:

- pausing with unfinished work;
- handing the project to another agent or model;
- leaving a long-running goal active;
- stopping after a blocker;
- finishing a substantial step where the next action is non-obvious.

Keep it compact:

```text
## Current Goal
## Current State
## Changed Files
## Last Verification
## Next Actions
## Blockers
```

Do not use `HANDOFF.md` as a permanent work log. Move durable work history to `WORKLOG.md`, accepted behavior to `SPEC.md`, accepted requirements to `REQUIREMENTS.md`, and accepted decisions to `DECISIONS.md`.

## Test Index Operations

`docs/tests/test-index.md` is the compact authoritative TDD catalog. Read it before implementation or behavior changes.

Update it when:

- a new requirement needs verification;
- an existing requirement changes;
- a test command, detail case, or status changes;
- a bug fix adds regression coverage.

Each entry should identify the test id, requirement, purpose, command, detail file, and status.

Do not put full test procedures, long fixtures, logs, or red/green/refactor notes in `docs/tests/test-index.md`. Put detailed cases under `docs/tests/cases/`, executable tests under `tests/`, compiled test artifacts under `build/tests/`, and active task TDD status in `TESTS.md`.

## Decision Log

`DECISIONS.md` is the one append-only accepted decision log for non-secret project decisions.

Use it when:

- the user accepts a design or workflow choice that should remain stable;
- a prior decision affects the current task;
- a decision is superseded and the replacement must be traceable.

Entry format:

```text
## YYYY-MM-DD: <decision title>

- Status: Accepted | Superseded
- Context:
- Decision:
- Consequences:
- Supersedes:
```

Do not split decisions into current and old files. Keep superseded decisions in the same file and append a newer entry that references the older one.

Do not store secrets, private infrastructure details, personal data, private remote URLs, or private-only business context in `DECISIONS.md`. Put non-public decisions in the sibling private repository when one is used, and keep only a sanitized public decision or pointer in this project when needed.

## Public Changelog

`CHANGELOG.md` is the public release history. It follows Keep a Changelog and must stay safe for a public repository page.

Use:

- `## [Unreleased]` at the top.
- release headings like `## [1.2.3] - YYYY-MM-DD`.
- only these change sections: `Added`, `Changed`, `Deprecated`, `Removed`, `Fixed`, `Security`.

Use `scripts/changelog-entry.sh <Section> <message>` for simple entries.

Do not use `CHANGELOG.md` as a private work log. Put local AI work history in `WORKLOG.md`.

If the user asks to refresh the changelog rules, fetch `https://keepachangelog.com/` and update this local guidance before changing changelog behavior.
