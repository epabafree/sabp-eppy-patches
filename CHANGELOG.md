# Release notes

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
