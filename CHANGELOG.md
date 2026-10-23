# Release notes

## v0.6.0: v2 spec rounds B and C (layout foundation, settings, library)

### Layout
- **One icon set:** Google's Material Symbols (Rounded, 24 dp grid, Apache 2.0) drawn as vectors, with
  filled variants for selected navigation items. Replaces the hand-drawn icons.
- **Navigation items are one component** (icon + label in a single container) used by the bottom bar,
  rail and floating dock. Selecting a tab only changes colours, never sizes. Navigation labels scale with
  the font setting up to 130 % so four tabs fit at 200 %.
- **Mini-player has its own slot above the navigation.** Lists get a bottom inset equal to the dock's
  measured height, so the last item is always reachable. Gesture and 3-button navigation are handled;
  the dock steps aside while the keyboard is open; sheets keep their content above the navigation bar.
- **Mini-player gestures:** swipe up opens the player, swipe down pauses and hides it (with Undo),
  sideways changes chapter (or pauses, or nothing: Settings → Mini-player). TalkBack actions for all.

### Settings
- **Player settings in eppy's design** (Settings → Player settings): auto-rewind, short/long skip
  intervals, headset and interruption behaviour, sleep-timer length, shake to extend, statistics. Each
  row uses SABP's own key, so the classic screens show the same values and changes apply immediately.
- **Back up, restore and reset eppy settings** (JSON file). SABP's data is never touched. eppy settings
  now carry a schema version; updates never overwrite your choices.

### Library
- **Pull to refresh** (or the refresh button): reloads progress and states from SABP and checks your
  library folders in the background for new, missing and moved books, with a summary you can dismiss
  or stop. Adding the new books is SABP's own Full scan, one tap away.
- **Sort** by recently played, title, progress, date added or length; **filter** all / in progress /
  not started / finished; **grid or list** view with progress bars and time left. Choices are remembered.

### Quality
- **UI tests in CI:** every shared component is rendered on the JVM (Paparazzi) in all six styles, light
  and dark, at 100 % and 200 % font, with automatic checks that icons sit centred on their labels and
  indicators, labels aren't clipped, targets are at least 48 dp, and selection doesn't change layout.


## v0.5.0: v2 spec round A (inventory, diagnostics, safe patching)

### Added
- **Switch to eppy inside Classic SABP**: in the classic player's ⋮ menu and at the top of SABP's Settings.
  Playback isn't touched: it lives in SABP's service, not in either screen.
- **Diagnostics page** (Settings → Help and bug reports → Diagnostics, or tap the version line five times):
  versions, which SABP targets the patch found, a runtime check, feature switches, storage used by eppy,
  recent errors, the last crash, and a log you can view, copy or export.
- **Feature switches**: Read along, online book information, listening log, blur, term lookup and cover
  gestures can each be turned off from Diagnostics, without re-patching.
- **Native page back stack**: eppy screens open over the current tab and Back returns to where you were.
- **docs/inventory.md**: every SABP setting (key, type, default, screen, status), the data model, the
  navigation map, the hook list with fallbacks, and the compatibility matrix.

### Changed
- **The patch can no longer half-apply.** It resolves every SABP target before changing anything. A missing
  required target stops the patch with a clear message; a missing optional target leaves a safe stub, and
  Diagnostics names the hidden feature.
- New optional hooks for later rounds: speed setter, sleep-timer countdown, character list.


## v0.4.0: stability release (renamed to eppy)

### Fixed
- **Unexpected returns to the classic player.** v0.3.0 added a new screen but left SABP's `PlayerActivity`
  reachable from the notification, widgets, the library's "open book", voice search and Android's task restore. A
  single hook at the start of `PlayerActivity.onCreate` now routes all of them to the chosen player.
- **Notification tap** now opens eppy's player. Playback controls keep using SABP's single playback service and session.
- **Read-along could never match (v0.3.0 defect).** The audio pipe was closed before Android delivered the
  recognition request, so every request failed. The pipe now stays open for the whole session.
- **Read-along crash loop:**
  - Whole-book indexing ran on the main thread, and automatic matching restarted within seconds of every launch.
  - Heavy work now runs on a background thread, automatic matching never starts at launch, and every stage is wrapped
    in a crash marker. After an abnormal exit, read-along turns automatic work off and explains, instead of repeating it.
- **Icon and label misalignment:**
  - Removed a line-spacing multiplier that lifted every label above its icon.
  - Re-centred the Rewind, Fast forward, Speed and Bookmark-add icons and removed a double offset on the play icon.
  - Tab and navigation labels no longer change weight (and width) when selected.
  - The bookmark chip reserves the width of "Remove bookmark", so it doesn't shift its neighbours.

### Added
- **Player interface** setting (eppy / Classic SABP player), plus a long-press app shortcut back to eppy.
- **Read along rebuilt as stages:** extract → prepare → **chapter-title alignment** (no permission needed) → optional
  listening matches → incremental, versioned saving. It also gets Stop, Rebuild and Remove pairing, named error messages,
  ambiguity rejection, and highlighting that reflects how reliable the position is.
- **Book information sources:** Open Library (no account) and Google Books (your API key), with candidate review,
  source labels, cache, Test buttons and conservative spoiler protection.
- **Diagnostics:** an operation log, a crash recorder (copyable report) and a runtime self-check of every bridge into SABP.
- **Documentation:** Morphe comparative analysis, navigation audit, test record (docs/).

### Changed
- **Speech matching** is now **opt-in** (patch option off by default; no microphone permission unless enabled).
- **Naming:** user-facing name is now **eppy**, and the patch is called "eppy" in Morphe Manager. Internal identifiers are unchanged.

### Not verified
No on-device testing has been done by the developer for this release. See docs/TESTING.md.

## v0.3.0
Player sheet (Chapters, Read along, Bookmarks, Details, Up next), circular play button, bookmark toggle, six styles, read-along prototype, credits.

## v0.2.0
First Eppy UI: Home, Library, Now Playing, mini-player, adaptive navigation, Modern font.

## v0.1.0
Material You theme for the classic screens.
