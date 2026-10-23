# SABP Eppy Patches

[Morphe](https://morphe.software) patches that give **Smart AudioBook Player** a modern look and new features, without rebuilding the app.

Supported version: **11.7.8** (`ak.alizandro.smartaudiobookplayer`).

## Patches

<!-- PATCHES_START EXPANDED -->
All patches can be picked independently, and they stack with Hooman's "Unlock Full" patch.

### Eppy UI

A new Material 3 interface on top of SABP's own playback engine. Your library, positions, bookmarks and settings stay the same.

- **Home:** greeting, search, a big *Continue listening* card (cover, chapter, % complete, time left, play button) and shelves for In progress, Not started and Finished.
- **Library:** an adaptive cover grid, a segmented filter (All, Listening, New, Finished) and Recent/A–Z sorting.
- **Now Playing:** a large rounded cover with an accent glow, an expressive title, a thick M3 seek slider (an optional static waveform), a big play button that changes shape between play and pause, and chips for speed, sleep, bookmark, bookmarks and chapters. A *More* bottom sheet holds Characters, History, Statistics and the classic player.
- **Floating mini-player:** sits above the navigation and expands into the full player.
- **Adaptive layout:** a bottom navigation bar on phones, a navigation rail on medium windows, and a persistent two-pane player on tablets, foldables and desktop windows.
- **Colour:** wallpaper (Material You), per-book from the cover art, or a custom colour. System, Light, Dark and Black themes. Contrast is always checked.
- **Accessibility:** TalkBack labels and announcements, 48dp touch targets, scalable text, high contrast, reduce motion, and haptics you can turn off.
- **Keyboard shortcuts:** Space/K, J/L, N/P, B, Ctrl+F, 1–4 and Esc.

| Option | Default | What it does |
|---|---|---|
| Open Eppy UI on launch | on | Off = the classic player stays the start screen and you get a second "Eppy" icon. |
| Default theme | Follow system | System / Light / Dark / Black (AMOLED). |
| Default accent | Wallpaper | Wallpaper / Book cover / Custom colour. |
| Custom accent colour | `#E8735A` | Used for "Custom colour" and on Android 11. |

### Material You theme

Recolours the **classic** SABP screens. It replaces the old Holo-blue accent with your wallpaper colours (Android 12+) or a colour you choose, tints the backgrounds, and rounds dialogs, sheets, cards and progress bars.

| Option | Default | What it does |
|---|---|---|
| Custom accent colour | *(empty)* | Empty = wallpaper colours. A hex like `#8AB4F8` = that accent on every Android version. |
| Tint backgrounds | on | Material You tinted surfaces for Dark/Light. Black stays pure black. |
| Rounded shapes | on | 28dp dialogs, rounded sheets/cards/buttons, thicker pill-shaped progress bars. |

### Modern font

Uses the Outfit font (SIL OFL) across the classic SABP screens and keeps each label's weight.

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

## How Eppy UI talks to SABP

The UI is a Morphe extension (`extensions/sabp`) written with plain Android views. It bundles no AndroidX or
Compose, so it can't clash with the libraries inside SABP. It:

- sends PlayerService's own intent actions (the same ones SABP's notification, widgets and Wear OS use) to play, pause, skip, bookmark, set the sleep timer and toggle speed;
- reads title, chapter, cover, position and speed from SABP's media session;
- calls five PlayerService methods (book lists, covers, seeking) through `EppyBridge` stubs. The patch fills these in by reading the
  Android Auto browser service and the classic player's seek bar callbacks, so obfuscated names are never hard-coded.

## Layout

```
patches/src/main/kotlin/sabp/morphe/patches/smartaudiobook/
  Compatibility.kt                  target app + version
  ui/EppyUiPatch.kt                 manifest + bridge wiring for the Eppy UI
  theme/MaterialYouThemePatch.kt    classic screens: colours, shapes, dialogs
  theme/ModernFontPatch.kt          classic screens: Outfit font (+ shared font asset patch)
patches/src/main/resources/sabp/fonts/  Outfit variable font (OFL)
extensions/sabp/src/main/java/app/morphe/extension/sabp/
  EppyActivity.java                 shell: adaptive nav, mini-player, back, keyboard
  EppyPlayer.java                   media session state, commands, library
  EppyBridge.java                   hooks + stubs filled in by the patch
  EppyTheme.java                    M3 colour roles, cover accent, contrast, font
  EppyFont.java                     runtime font for classic screens
  ui/                               Home, Library, Settings, Now Playing, slider, icons, sheets
```

## Licence

GPLv3. Project scaffolding is based on the Morphe patches template. Outfit font © The Outfit Project Authors, SIL Open Font License 1.1.
