# Requirements Log

Historical requirement changes live here. Current accepted requirements belong in root
`REQUIREMENTS.md`.

### 2026-07-12: initial requirements

- Source: project owner, first request.
- Change: created R1-R16.
- Reason: new project. Compatibility with Android 4.4 was named the first priority and installability
  of the APK on old phones the second, so those became R1 and R2.
- Current requirement affected: all.

### 2026-07-12: tall-layout bottom line

- Source: project owner, first request ("작게 년도하고 월 초 표시").
- Change: R8 reads "small line with the year and the seconds".
- Reason: the month and the day already appear in the row above (`Sun, July 10`), so the bottom line
  was read as year plus seconds. Confirm with the owner; changing it is a one-line change in
  `ClockText.smallLine`.
- Current requirement affected: R8.
