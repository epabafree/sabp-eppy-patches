# SABP Eppy Patches

[Morphe](https://morphe.software) patches that give **Smart AudioBook Player** a modern look and new features, without rebuilding the app.

Supported version: **11.7.8** (`ak.alizandro.smartaudiobookplayer`).

## Patches

<!-- PATCHES_START EXPANDED -->
> **Best used with [Hooman's Morphe Patches](https://github.com/arandomhooman/hoomans-morphe-patches) for SABP.**
> Add both patch sources in Morphe Manager and apply Hooman's SABP patch together with these. Everything here
> was tested on Hooman's SABP 11.7.8 build. (Not affiliated with or endorsed by Hooman.)

Every patch can be picked on its own.

### Eppy UI

A new Material 3 interface on top of SABP's own playback engine. Your library, positions, bookmarks and settings stay the same.

- **Home:** a greeting, search, a *Continue listening* card, and shelves for In progress, Not started and Finished.
- **Library:** an adaptive cover grid, a segmented filter (All / Listening / New / Finished) and Recent or A–Z sorting.
- **Now Playing:** a cover styled by the chosen look, a thick seek slider or a static waveform, a circular play/pause button, and chips for speed, sleep, **Add/Remove bookmark** and Chapters.
- **Player sheet** (drag up or tap a tab, like YouTube Music). Nothing in it opens the old interface:
  - **Chapters:** embedded m4b/cue chapters or one entry per file, with lengths, the current chapter highlighted with progress, and tap to jump.
  - **Read along:** pair an EPUB or PDF and follow the narration (see below).
  - **Bookmarks:** the same `bookmarks.sabp.xml` the classic screens use. Tap to jump, remove with Undo, add at this moment or at the start of the chapter.
  - **Details:** length, chapters and progress, the description from `info.txt` or the paired EPUB, each labelled with its source.
  - **Up next:** the remaining chapters, plus a link to SABP's book queue.
- **Floating mini-player**, a bottom bar, floating pill or navigation rail, and a two-pane player on tablets, foldables and desktop windows.
- **Accessibility:** TalkBack labels and announcements, 48dp targets, scalable text, high contrast, reduce motion and optional haptics.
- **Keyboard shortcuts:** Space/K, J/L, N/P, B, Ctrl+F, 1–4 and Esc.

| Option | Default | What it does |
|---|---|---|
| Style | Material You | Starting look: Material You, Ember, Midnight, Lavender, Paper or Mono. You can switch any time in Eppy Settings → Style. |
| Read-along speech matching | on | Lets Read along use Android's on-device speech recogniser and adds the microphone permission, which Android's speech service requires. You're asked first, and the mic is never used. |
| Open Eppy UI on launch | on | Off = the classic player stays the start screen and you get a second "Eppy" icon. |
| Default theme | Follow system | System / Light / Dark / Black (Material You style). |
| Default accent | Wallpaper | Wallpaper / Book cover / Custom colour (Material You style). |
| Custom accent colour | `#E8735A` | Used for "Custom colour" and on Android 11. |

#### Styles (reference images → screens)

| Style | Reference image | Home | Now Playing | Navigation |
|---|---|---|---|---|
| Material You | (default) | Greeting + Continue card + shelves | Cover with accent glow | M3 bar / rail |
| Ember | dark + coral "Morning, Yulia" / "Now Playing" | Avatar greeting, gradient Continue card, cover shelves | Cover inside a dashed ring with a progress arc and thumb, waveform, coral circle play | Floating dark pill, white selected circle |
| Midnight | dark purple "Hi, Vincent" | Two-tone headline, lilac Continue card | Lilac player that shows the **read-along text** in place of the cover, rounded-square play | M3 bar |
| Lavender | lavender "Library / Activity" + "Atomic Habits" Listen/Read | Headline, white glass cards | Cover card, purple waveform, outline play | Floating white pill, purple selected circle |
| Paper | cream "Discover a smarter way to read" | Greeting + subtitle, UPPERCASE cover labels | Cover card, red progress | Cream bar |
| Mono | white/black/yellow "Explore" | Centred title, search pill, "Book of the day" | Cover card, yellow waveform, black rounded-square play | Floating white bar, dark selected square |

#### Read along (prototype)

1. Open the player sheet → **Read along** → **Choose EPUB or PDF**. The pairing is saved per audiobook and survives restarts.
2. The text is extracted on the device: the EPUB spine order with headings, or PDF text on Android 15+. Other PDFs are shown page by page.
3. **Matching:** Eppy decodes a 12-second clip of the audiobook, transcribes it with Android's **offline** recogniser, and finds those words in the text. The search is a voting match across the whole book that weights rare words more. It samples a few points across the book right after pairing, then about every 90 s while you listen. Anchors are saved.
4. **Honest highlighting:** the sentence is highlighted only when the position comes from matched audio or exact timings. Estimates highlight the whole paragraph. The status line shows *Estimated*, *Matching…*, *Matched at N points* or *Exact sync*.
5. **Tap** a paragraph to play from it. **Long-press** it and choose "I'm hearing this now" to correct the sync by hand.
6. **Exact sync:** EPUBs with EPUB 3 Media Overlays (e.g. made by [Storyteller](https://gitlab.com/storyteller-platform/storyteller)) use their own narration timings.

Measured on *Pride and Prejudice* (125k words): simulated 12-second transcripts with ~22% of words dropped or misheard were located correctly
in 92.7% of tries and wrongly in 0%. The rest are skipped and retried. Each search takes 0.4 ms.

**Limitations:**
- Speech matching needs Android 13+ and an offline speech model for the book's language.
- On other devices, sync is manual (long-press a paragraph).
- Scanned PDFs need OCR first, which isn't built in.
- Abridged recordings or a different edition give fewer matches; the status line says so.

### Material You theme

Recolours the **classic** SABP screens: wallpaper colours (Android 12+) or a custom accent, tinted backgrounds, and rounded dialogs, sheets and progress bars.

| Option | Default | What it does |
|---|---|---|
| Custom accent colour | *(empty)* | Empty = wallpaper colours. A hex like `#8AB4F8` = that accent on every Android version. |
| Tint backgrounds | on | Material You tinted surfaces for Dark/Light. Black stays pure black. |
| Rounded shapes | on | 28dp dialogs, rounded sheets/cards/buttons, thicker pill-shaped progress bars. |

### Modern font

Uses the Outfit font across the classic SABP screens and keeps each label's weight.

<!-- PATCHES_END -->

## Status

| Round | What | State |
|---|---|---|
| 1 | Chapters native (no legacy screen), circular play/pause, Add/Remove bookmark from saved data | Done in 0.3.0 |
| 2 | Player sheet: Chapters / Read along / Bookmarks / Details / Up next | Done in 0.3.0 |
| 3 | Reference-image styles (6) as a patch option + in-app switch | Done in 0.3.0 |
| 4 | Read-along prototype: pairing, extraction, matching, read-along sheet | Prototype in 0.3.0 |
| 5 | Open-source review + credits | Done, see [CREDITS.md](CREDITS.md) |

**Not built yet** (each will be its own patch later):
- a full EPUB/PDF reader with advanced sync
- online metadata lookup (author, narrator, series, summaries, characters) through pluggable providers
- duplicate detection and merging
- search by chapter name and by spoken content
- playback profiles
- a standalone Wear OS app

**Still classic screens:** Characters, Statistics, History, Book queue, player settings, the equalizer and the full speed dialog
open SABP's own screens (they're labelled "Classic screen" wherever they appear). Chapters and bookmarks never do.

**Tested:** everything compiles, patches apply cleanly on SABP 11.7.8 together with Hooman's patch, and decompiling the
result shows every bridge resolves to the right SABP method. The read-along matcher and EPUB parser were tested on the JVM.
**On-device testing is done by users.** Please report issues with screenshots.

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
  EppyActivity.java                 shell: adaptive nav, mini-player, back, keyboard, picker, undo
  EppyPlayer.java                   media session state, commands, library, whole-book position, jumps
  EppyBook.java                     files/chapters/bookmarks read from SABP's serialized data
  EppyBookmarks.java                bookmark state backed by bookmarks.sabp.xml
  EppyBridge.java                   hooks + stubs filled in by the patch
  EppyTheme.java                    M3 colour roles, 6 styles, cover accent, contrast, fonts
  EppyFont.java                     runtime font for classic screens
  read/                             read-along: EPUB/PDF extraction, alignment, speech matching
  ui/                               Home, Library, Settings, Now Playing, player sheet + tabs, widgets
```

## Credits

See **[CREDITS.md](CREDITS.md)** for every project used, with its author, licence and how it's used. In short: Smart AudioBook Player;
[Hooman's Morphe Patches](https://github.com/arandomhooman/hoomans-morphe-patches) (recommended companion); the Morphe patches template;
the Outfit and Plus Jakarta Sans fonts (SIL OFL 1.1); and compatibility with [Storyteller](https://gitlab.com/storyteller-platform/storyteller) readaloud EPUBs.

## Licence

GPLv3, with the Morphe template [NOTICE](NOTICE). Project scaffolding is based on the Morphe patches template. Outfit font © The Outfit Project Authors, SIL Open Font License 1.1.
