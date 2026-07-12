# T002: Burn-in Shift

## Requirement

R14: shift the drawn content slowly, in small steps, within a small pixel range.

## Verification Method

JUnit 5 on a plain JVM: `tests/java/com/reteclock/core/BurnInShiftTest.java`.

## Assertions

- The offset never leaves `[-max, +max]` on either axis.
- The amplitude is 3% of the shorter screen edge, clamped to 4..32 pixels.
- A position is held for a whole step (one minute) and then changes.
- All 24 positions of a cycle are distinct, and the path returns to its start after a full cycle.
- Consecutive positions are no further apart than the maximum shift, so the movement stays subtle.
