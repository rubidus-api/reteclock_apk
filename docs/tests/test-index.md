# Test Index

Short authoritative TDD catalog.

| ID | Requirement | Purpose | Command | Detail | Status |
|---|---|---|---|---|---|
| T001 | R6, R7, R8, R10 | Clock strings: 24-hour, zero padded, English names, second-aligned ticks | `scripts/test.sh` | docs/tests/cases/T001-clock-text.md | passing |
| T002 | R14 | Burn-in offsets: bounded, distinct per cycle, small steps, exact period | `scripts/test.sh` | docs/tests/cases/T002-burn-in-shift.md | passing |
| T003 | R7, R8, R9 | Layout geometry for wide and tall screens, and text fitting | `scripts/test.sh` | docs/tests/cases/T003-clock-layout.md | passing |
| T004 | R1, R2 | The APK installs and runs on a real API 19 system image | `scripts/verify-kitkat.sh` | docs/tests/cases/T004-kitkat-install.md | passing |
