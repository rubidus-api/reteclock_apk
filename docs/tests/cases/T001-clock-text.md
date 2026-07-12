# T001: Clock Text

## Requirement

R6, R7, R8, R10: 24-hour time with seconds, weekday, month, day and year; English names regardless
of the device locale.

## Verification Method

JUnit 5 on a plain JVM: `tests/java/com/reteclock/core/ClockTextTest.java`.

## Assertions

- Every field of a known instant formats as expected (`13`, `45`, `25s`, `Fri`, `July 10`, `2026`).
- Hours, minutes and seconds are zero padded; midnight is `00`, not `24` or `12`.
- Weekday and month names stay English when the default locale is Korean.
- `millisToNextSecond` returns the delay that aligns the next redraw with the second boundary.
