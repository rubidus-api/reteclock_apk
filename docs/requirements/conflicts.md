# Requirement Conflicts

Contradictions, supersessions, and precedence decisions live here.

### 2026-07-12: old-device support versus installability on current Android

- Conflict: R1/R2 want the lowest possible SDK levels; R3 wants installability on current Android,
  which refuses `targetSdkVersion` below 23.
- Resolution: split the two axes. `minSdkVersion 9` and compiling against API 19 serve R1/R2;
  `targetSdkVersion 28` serves R3 and changes nothing on old devices, because platform behavior
  changes only apply on the platform that introduced them.
- Superseded text: none.
- Current source of truth: `SPEC.md`, `DECISIONS.md` (2026-07-12 minSdk/targetSdk entry).
