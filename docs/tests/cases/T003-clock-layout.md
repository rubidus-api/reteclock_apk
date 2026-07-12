# T003: Clock Layout

## Requirement

R7, R8, R9: the wide and tall arrangements, and orientation changes.

## Verification Method

JUnit 5 on a plain JVM: `tests/java/com/reteclock/core/ClockLayoutTest.java`.

## Assertions

- Wide: five slots; the big time sits in the left half and is the largest; seconds, weekday,
  month/day and year form a top-to-bottom column to its right; the column is centered vertically and
  fits on screen.
- Tall: four slots; hour and minute are equally large; date and small line follow, each smaller than
  the one above; all are horizontally centered and inside the screen.
- A square screen uses the tall layout.
- Secondary lines are dimmer than the big time but not invisible.
- `shrinkToFit` scales oversized text down to its box and leaves fitting text untouched.
