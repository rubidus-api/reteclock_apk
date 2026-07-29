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

1. Tag `v0.2.1` on the merged commit and push the tag. F-Droid builds from the tag, so it must
   exist before the merge request.

   ```sh
   git tag -a v0.2.1 -m 'reteclock 0.2.1'
   git push origin v0.2.1
   ```

   0.2.1 is a packaging release: the clock is unchanged. It exists because `v0.2.0` points at a
   commit with no `fastlane/` and no `--unsigned`, so a recipe naming that tag would build and then
   fail to find the file `output:` names. Do not move `v0.2.0` to fix that — it is published, and
   the 0.2.0 APK on the releases page hangs off it.

   Publishing a GitHub release for 0.2.1 is separate and optional. The tag alone is enough for
   F-Droid, and while no 0.2.1 release exists the README's download link keeps resolving to the
   0.2.0 one. If you do publish it, build the asset with `scripts/build.sh --release`, name it
   `reteclock-0.2.1.apk`, and update the link in the README to match.

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
   (`dist/reteclock-0.2.1-unsigned.apk`) and let `AutoUpdateMode` rewrite it on the next release.

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
4. Build with `scripts/build.sh --release` and publish a GitHub release for that tag with the APK
   named `reteclock-<versionName>.apk`. This is **not** optional any more: `Binaries:` points at
   that URL and the F-Droid build fails without it.
5. Update the download link in the README, which names the file explicitly.

No merge request is needed for an ordinary release.

## Two things to know

**The signature is ours, because this is a reproducible build.** F-Droid compiles the app on its
own server, compares the result against the APK on the releases page, and ships that file. Both
downloads carry the same signature, so a phone can move between them without uninstalling.

Two obligations come with that. Every release needs a signed APK published at
`releases/download/v<version>/reteclock-<version>.apk`, because `Binaries:` points there and the
build fails without it. And the signing key has to survive: if it is lost, no future version can
update an installed reteclock, and `AllowedAPKSigningKeys` cannot be changed to a new one without
F-Droid treating it as a different app.

**The v1 signature is no longer a risk.** reteclock declares `minSdkVersion 9` and needs a v1 (JAR)
signature, because Android 2.3 through 6 accept nothing else (requirement R2). Under a reproducible
build F-Droid ships the APK built here, signed by `scripts/build.sh --release`, which enables v1,
v2 and v3 explicitly. There is nothing left to hope for. Still worth one check on the first
published build:

```sh
apksigner verify --min-sdk-version 9 --verbose reteclock.apk
```

`Verified using v1 scheme (JAR signing): true` is the line that matters.
