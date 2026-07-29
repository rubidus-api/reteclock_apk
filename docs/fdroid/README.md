# Publishing reteclock on F-Droid

F-Droid does not accept a finished APK. It clones this repository at a tagged commit, builds the
app on its own build server, and signs the result with the F-Droid key. Everything in this
directory exists to make that build possible and repeatable.

## What is already in place

| Piece | Where | Why |
|---|---|---|
| Unsigned build | `scripts/build.sh --unsigned` | F-Droid rejects a build output that is already signed. This mode stops after `zipalign` and never touches a keystore. |
| Store metadata | `fastlane/metadata/android/en-US/` | F-Droid reads the title, descriptions, icon, screenshots and per-release changelogs out of the source tree at the build commit. |
| Build recipe | `docs/fdroid/com.reteclock.yml` | The file to copy into a `fdroiddata` fork as `metadata/com.reteclock.yml`. |

The unsigned build is covered by test T005 (`tests/build/test-unsigned.sh`), which asserts the APK
exists, stays single dex, carries no signature of any scheme, is zipaligned, and that the run
created no development key.

## One-time submission

These steps need a GitLab account. Nothing here needs a GitLab account to *prepare* — only to
submit.

1. Make sure a tag exists whose tree contains this material, and decide which one it is.

   **This is not automatic.** The existing `v0.2.0` tag was created before F-Droid support and
   points at a commit with no `fastlane/` and no `--unsigned` in `scripts/build.sh`. If the recipe
   names that tag, the F-Droid build fails: the recipe's `output:` file is never produced.

   Two honest ways out, and one to avoid:

   - **Release 0.2.1** (recommended). Raise `versionCode` to 3 and `versionName` to `0.2.1`, add
     `fastlane/metadata/android/en-US/changelogs/3.txt`, tag `v0.2.1`, push the tag, and set the
     recipe's `versionName`, `versionCode` and `commit` to match. Nothing about the app changes,
     but the tag is new, so nothing published has to be rewritten.
   - **Name the merge commit** in the recipe instead of a tag: `commit: <full sha>`. F-Droid
     accepts a commit hash. `UpdateCheckMode: Tags` still picks up tags for later releases. Use
     this if you would rather not spend a version number.
   - **Do not move the `v0.2.0` tag.** It is published, the 0.2.0 APK on the releases page hangs
     off it, and anyone who already fetched it would get a tag that disagrees with yours.

   ```sh
   git tag -a v0.2.1 -m 'reteclock 0.2.1'
   git push origin v0.2.1
   ```

2. Fork <https://gitlab.com/fdroid/fdroiddata>, clone the fork, and branch off `master`. Name the
   branch after the application id:

   ```sh
   git checkout -b com.reteclock
   ```

3. Copy the recipe into place:

   ```sh
   cp /path/to/reteclock_apk/docs/fdroid/com.reteclock.yml metadata/com.reteclock.yml
   ```

4. Check it locally with the F-Droid server tools. The simplest route is the published container
   image, which already has the SDK and `fdroidserver` in it:

   ```sh
   fdroid lint com.reteclock
   fdroid rewritemeta com.reteclock
   fdroid checkupdates --allow-dirty com.reteclock
   fdroid build com.reteclock
   ```

   `fdroid build` is the one that matters: it must produce the APK. If `fdroid lint` objects to the
   `%v` placeholder in `output:`, replace it with the literal file name
   (`dist/reteclock-0.2.0-unsigned.apk`) and let `AutoUpdateMode` rewrite it on the next release.

5. Commit as `New App: com.reteclock`, push the branch to the fork, and open a merge request
   against <https://gitlab.com/fdroid/fdroiddata>. The merge request *is* the application; there is
   no separate form to fill in and nobody to email.

6. Answer review comments on the merge request. After it merges, the F-Droid build server picks the
   app up, usually within a day or two, and it then appears in the main repository.

Opening an issue at <https://gitlab.com/fdroid/rfp> first is optional. It is for asking whether an
app would be accepted, or for asking somebody else to package it. Neither applies when you are the
author and the recipe is already written.

## Every release after that

`UpdateCheckMode: Tags` and `AutoUpdateMode: Version v%v` mean F-Droid notices new tags by itself
and generates the next build block. So a release is:

1. Raise `android:versionCode` and `android:versionName` in `src/android/AndroidManifest.xml`.
   The version code must increase; F-Droid will not publish a build that does not.
2. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`. The file name is the version
   **code**, not the name.
3. Commit, tag `v<versionName>`, push the tag.

No merge request is needed for an ordinary release.

## Two things to know

**The signature changes.** The F-Droid build is signed with the F-Droid key, not the key used for
the APKs on the GitHub releases page. Android will not upgrade one to the other in place. Somebody
running the GitHub APK has to uninstall it before installing the F-Droid one, and loses their
settings in the process. This is the normal cost of an F-Droid listing.

**Check the signature scheme on the first F-Droid build.** reteclock declares `minSdkVersion 9` and
requires a v1 (JAR) signature, because Android 2.3 through 6 accept nothing else (requirement R2).
F-Droid signs with `apksigner`, which decides the schemes from the APK's own `minSdkVersion`, so a
v1 signature is expected. That has not been confirmed on a real F-Droid-built artifact. The first
thing to do once the app is published is download it and run:

```sh
apksigner verify --min-sdk-version 9 --verbose reteclock.apk
```

`Verified using v1 scheme (JAR signing): true` is the line that matters. If it is false, the app
installs on modern Android but not on the old phones it was written for, and that needs raising on
the merge request.
