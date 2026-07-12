# DECISIONS

Single append-only accepted decision log.

Read this only when the current task needs prior decisions, rationale, or supersession history.

## 2026-07-12: No Gradle; build with the SDK command-line tools

- Status: Accepted
- Context: The Android Gradle Plugin drops support for old minimum SDKs, pulls in a large tool
  chain, and hides what actually goes into the APK. The project needs `minSdkVersion 9`, a single
  dex, and exact control over signing.
- Decision: Build with `aapt2`, `javac`, `d8`, `zipalign` and `apksigner` driven by POSIX shell
  scripts (`scripts/build.sh`). No Gradle, no AGP, no wrapper.
- Consequences: The build is a few dozen lines, needs only a JDK and the SDK command-line tools,
  and produces a 54 KB APK. There is no IDE project file; contributors run the scripts.

## 2026-07-12: minSdkVersion 9, targetSdkVersion 28, compile against API 19

- Status: Accepted
- Context: The clock must work best on Android 4.4 and install as widely as possible in both
  directions. Android 14 refuses to install an APK whose `targetSdkVersion` is below 23, so simply
  targeting 19 would lock the app out of current phones. A high `targetSdkVersion` only activates
  new behavior on new platforms, so it costs nothing on 4.4.
- Decision: `minSdkVersion 9`, `targetSdkVersion 28`, and compile against `android-19/android.jar`.
  Calls that need a platform newer than 9 are guarded with `Build.VERSION.SDK_INT`.
- Consequences: The compiler prevents accidental use of post-KitKat APIs. Newer Android versions
  install the APK. The few newer features used (immersive mode, Daydream) degrade cleanly on older
  devices.

## 2026-07-12: Sign with v1 plus v2 and v3

- Status: Accepted
- Context: Dalvik on Android 4.4 only understands the v1 (JAR) signature. Android 11 and newer
  expect v2 or better for apps that target recent platforms.
- Decision: Sign every APK with v1, v2 and v3 enabled, and pass `--min-sdk-version 9` to apksigner
  so it picks digests old devices accept.
- Consequences: One APK installs everywhere. `scripts/build.sh` prints the apksigner verification
  of all three schemes after every build.

## 2026-07-12: Draw the clock on a Canvas instead of using layout XML

- Status: Accepted
- Context: The two layouts differ enough that an XML layout per orientation would duplicate state,
  and text must scale to the screen rather than to a density bucket.
- Decision: One custom `View` draws every line with a `Paint`. The geometry lives in the pure-Java
  `ClockLayout`; the view only measures glyphs and draws them.
- Consequences: No layout XML, no support library, exact control over sizes. The layout is unit
  tested on a JVM because it holds no Android types.

## 2026-07-12: Burn-in shift walks a 24-position disc path

- Status: Accepted
- Context: A shift that only moves along a ring keeps the offset magnitude constant, and the first
  attempt (a Lissajous figure-eight) passed through the same center point twice, so two of the 24
  positions were identical.
- Decision: Advance the angle by one twenty-fourth of a turn per minute and cycle the radius through
  100%, 70% and 40% of the maximum shift. All 24 positions are distinct, consecutive positions stay
  close together, and the path closes after 24 minutes.
- Consequences: Burn-in protection spreads over a small disc. The property is asserted by unit tests
  (distinct positions, bounded amplitude, small steps, exact cycle).

## Template

### YYYY-MM-DD: <decision title>

- Status: Accepted | Superseded
- Context:
- Decision:
- Consequences:
- Supersedes:
