# SABP Eppy Patches

[Morphe](https://morphe.software) patches that give **Smart AudioBook Player** a modern look and new features, without rebuilding the app.

Supported version: **11.7.8** (`ak.alizandro.smartaudiobookplayer`).

## Patches

<!-- PATCHES_START EXPANDED -->
### Material You theme

Replaces the old Holo-blue accent with your wallpaper colours (Android 12+) or a colour you choose, tints the dark and light backgrounds to match, and rounds dialogs, sheets, cards and progress bars the way Material 3 does. The in-app Light, Dark and Black theme choice keeps working.

| Option | Default | What it does |
|---|---|---|
| Custom accent colour | *(empty)* | Empty = wallpaper colours. A hex like `#8AB4F8` = that accent on every Android version. |
| Tint backgrounds | on | Material You tinted surfaces for Dark/Light. Black stays pure black. |
| Rounded shapes | on | 28dp dialogs, rounded sheets/cards/buttons, thicker pill-shaped progress bars. |
<!-- PATCHES_END -->

## Using the patches

1. Install **Morphe Manager** on your phone.
2. In Morphe Manager, add this repository as a patch source: `https://github.com/epabafree/sabp-eppy-patches`.
3. Pick the Smart AudioBook Player 11.7.8 APK, choose the patches and options, and patch.

The patched app is signed with your own key, so it can't install over a Play Store copy. Make a backup in
SABP first (Settings → backup), uninstall, install the patched APK and restore.

## Building

The GitHub Actions workflows build everything. You don't need a PC.

1. Push this folder to the `sabp-eppy-patches` repository.
2. (Already done: the project points at `epabafree/sabp-eppy-patches`.)
3. Create a `dev` branch next to `main` (the release workflow back-merges releases into it).
4. In the repo settings, go to **Actions → General → Workflow permissions** and select **Read and write permissions**.
5. Push a commit to `main` whose message starts with `feat:` or `fix:`. The **Release** workflow builds
   `patches-x.y.z.mpp` and publishes it as a GitHub release, which Morphe Manager picks up.

Building locally works too: `./gradlew :patches:buildAndroid` (JDK 21 + Android SDK). Morphe's Gradle plugin
comes from GitHub Packages, so set `gpr.user` / `gpr.key` (a GitHub token with `read:packages`) in
`~/.gradle/gradle.properties`.

## Layout

```
patches/src/main/kotlin/sabp/morphe/patches/smartaudiobook/
  Compatibility.kt                 target app + version
  theme/MaterialYouThemePatch.kt   resource patch: colours, shapes, dialogs
extensions/                        (future) Kotlin code merged into the app for new features/screens
```

## Licence

GPLv3. Project scaffolding is based on the Morphe patches template.
