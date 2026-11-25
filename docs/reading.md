# Reading and navigation

[Yonaka Reader](../README.md)

## Home and Library

**Home** (**Cmd/Ctrl+Shift+H**) shows recently read books. **File → Library**, the books icon beside Home
on the toolbar, or **Cmd/Ctrl+Shift+L** opens the library at the top of its scrollable collection.
Both screens have a visible heading and replace the former tabs. Leaving the reader
saves your progress and unloads the book. The Library shortcut can be changed in
Settings alongside the other controls.

## Supported content

- Images: `jpg`, `jpeg`, `png`, and `gif`
- Archives: `zip` and `cbz`
- Natural file ordering, for example `page2` before `page10`
- Nested image paths inside ZIP/CBZ archives
- Single-page and two-page spreads
- Left-to-right comics and right-to-left manga reading modes
- Fit-to-screen, fit-width, fit-height, zoom, scrolling, and fullscreen modes
- Background image decoding with stale-request protection
- Bounded image caching and next-page prefetch

Animated GIFs play both as standalone files and inside ZIP/CBZ archives, including in
spreads. Sequential background decoding preserves frame offsets, disposal rules,
delays and loop counts, with two frames buffered ahead per visible page. Playback follows a monotonic timeline, so delayed UI ticks do not accumulate slowdown.
There is no frame-count or duration limit; completed passes reopen
the source. Memory for decoded pixels depends on canvas size, not animation length.
Leaving the page cancels playback and closes its file/archive stream. Covers
and sidebar thumbnails intentionally stay static.

Paired pages are displayed at a common height while preserving each page's aspect
ratio, even when their source resolutions differ. **Fit to window** fits the complete
spread and adjusts its scale when the window or thumbnail sidebar changes size.

In two-page mode, press **Shift+P** or select **View → Shift spread by one page** to show the cover alone and pair pages 2–3, 4–5, and so on. Landscape pages stay single. The reader remembers this choice for each book in either reading direction.

### Scrolling, zoom and panning

Scroll vertically with the wheel or horizontally with Shift+wheel. At the usual three-unit setting,
one wheel notch moves 48 logical pixels. Fractional trackpad movements accumulate on each axis.
While either dimension extends outside the viewport, scrolling and dragging pan the current page,
including at its edges. When the entire page fits, a horizontal swipe or left-button drag turns one page.
Swipe right to advance in manga mode and left in comics mode.

Toolbar and keyboard zoom keep the viewport center stable. Ctrl+wheel (or Command+wheel on macOS)
zooms around the pointer. Native macOS pinch uses the pointer inside the viewport and otherwise
uses its center. Windows/Linux use Ctrl+wheel, including trackpads
that emit that gesture. Manual zoom ranges from 5% to 2000%. Turning the page restores the last fit mode.

The packaged macOS app and executable JAR enable Java's macOS magnification API.
When running the main class directly from an IDE, add VM option
`--add-exports=java.desktop/com.apple.eawt.event=ALL-UNNAMED` to enable native pinch;
Ctrl/Command+wheel works without it.

## Reader interface

See the [illustrated toolbar guide](../README.md#toolbar-controls) for each button.

- A welcome screen provides a single Open action and a supported-format hint.
- The toolbar shows the displayed page or spread range and total page count. Click it or press G to jump to a page.
- Vector navigation icons replace font-dependent symbols and emoji.
- The toolbar has dedicated buttons for fit, zoom and reading direction. Hover over a button for its action and shortcut.
- Scale, two-page spread, reading direction and fullscreen states stay synchronized between toolbar, menus and shortcuts.
- Reading controls become available after a book opens. By default, Next on the last page or Previous on the first page keeps the current page and briefly shows “Last page” or “First page”. In Settings → Reading, choose Do nothing, wrap within the volume, or continue into the adjacent volume. At the ends of a series the reader stays on the page.
- Press **Escape** to leave fullscreen and continue reading in the window.

## Page thumbnails

Click the centered handle on the sidebar edge, or use **View → Thumbnails** / **T**,
to show or hide the left page panel. The handle remains available when collapsed; its visibility
is saved between launches. Click a thumbnail to open that page, or focus the list,
select a page with the arrow keys and press Enter. The current page or spread is
highlighted and follows reading progress. The panel is shown once a book is open.

Use **View → Grid** or the **Grid** button on the bottom toolbar (the grid button is at the foot of the sidebar in Fullscreen) to fill the reading area with a responsive
thumbnail grid, including while in Fullscreen. Click a page or select it with the
arrow keys and press Enter: the grid returns to the vertical sidebar and opens that
page. **Back to reader** or **Esc** returns without changing the page. Keyboard
navigation returns to the reader, and existing Fullscreen mode is preserved.
The grid reuses the sidebar cache and loads only the visible area plus its margin.

Thumbnails load on two dedicated background workers, visible pages first. A sliding in-memory cache keeps the visible range plus 10 pages before and
10 after it (usually about 25–30 previews, 144 × 192 logical pixels each, backed by up to 432 × 576 pixels for HiDPI screens). Scrolling
evicts previews outside this window and requests previews for the new viewport.
Changing books clears the preview cache. Unreadable previews retain their page number
so the remaining pages can still be browsed.

## Keyboard shortcuts

Open **Settings → Shortcuts** to see the current keys for every reader action. Select a row and choose **Assign…**, then press the new combination. If another action uses that key, confirm the move or cancel to keep both assignments. **Clear** removes all keys for the selected action. **Restore defaults** resets the whole list. Choose **Apply** or **OK** to save your changes and update the menus and toolbar hints.

Page navigation, zoom and reading-mode shortcuts are active while reading a book. Focusing a text field suspends them, including custom bindings, so you can type, move the caret and select text. Standard application chords such as `Cmd/Ctrl+O` remain available in the main window. Custom application shortcuts resume when focus leaves the field.

Default bindings:

| Key | Action |
| --- | --- |
| `Cmd/Ctrl+O` | Open a file |
| `Right`, `Page Down` | Next page |
| `Left`, `Page Up` | Previous page |
| `Shift+G` | Open the page grid |
| `Home`, `End` | First or last page |
| `Command+Shift+H` / `Ctrl+Shift+H` | Home / recently read |
| `Up`, `Down` | Previous or next volume |
| `H`, `W`, `S` | Fit height, width, or screen |
| `+`, `-` | Zoom in or out |
| `P` | Toggle two-page mode |
| `Shift+P` | Shift the two-page pairing by one page |
| `M`, `C` | Manga or comics reading mode |
| `Ctrl+Cmd+F` (macOS), `F11` (Windows/Linux) | Toggle fullscreen |
| `Escape` | Leave fullscreen |
| `G` | Go to page |
| `Cmd+Q` (macOS), `Ctrl+Q` (Linux), `Alt+F4` (Windows) | Exit |

## Recently read

The home screen shows up to 6 recently read books/series with a first-page cover and saved
page progress. Archive cards show the parent folder as the series title and the last opened
filename below it, including when only one volume has been read. The same list is available under **File → Open Recent**. Use
**File → Home** (Command+Shift+H on macOS, Ctrl+Shift+H elsewhere) to return to the shelf while a book is open.

**Group archives by folder** is enabled by default and can be disabled on the home screen.
Archives in the same parent folder share a card; Continue resumes the most recently
read chapter. **Chapters** shows the chapters on the card with individual open and
delete controls. The card's trash button clears the reading history of every chapter
on that card. Grouping examines up to 1,000 recent book states and loads up to 6 covers.
Grouping is by folder, not by filename guesses; keep different series in separate
folders or switch grouping off. This preference survives restarts.

Click a book to resume its saved page, spread mode and reading direction. Images
opened individually share the history entry of their containing directory.
Removing an entry clears its saved page and reading modes. Reopening starts a new
reading history. Files, library membership and completion marks are preserved. Missing or inaccessible sources remain visible
as unavailable. Reopen Home or Open Recent to refresh their availability after
reconnecting a drive. A moved book must currently be opened from its new location;
automatic move detection and transferring progress to a new path are future library work.

Covers are loaded in the background and kept only in bounded memory. An unreadable
cover uses a placeholder; opening the source reports any actual reading error.
Existing reading history is preserved by a transactional schema migration.

## Library

Open **File → Home → Library** to build an explicit shelf, separate from Recently read.
Use **Add folder…** to scan a library root. Each ZIP or CBZ directly in the root becomes a
book named after the file. Loose images in the root share one book. Each immediate
subfolder becomes a book containing its archive chapters in natural order and its loose
images as one chapter. Deeper directories and symbolic links are skipped.

Review book titles and select chapters before importing. A subfolder containing
both images and archives starts unchecked so you can choose which to include. The preview reports
archives that cannot be read or contain no supported pages. Scanning can
be cancelled. Previously imported chapters keep their original book and reading state
when the same files are found from another root.

Library and Recently read use the same card layout. In the library, click the cover or title
to view chapters and their saved positions. **Continue reading** opens
the last-read chapter with the existing page, reading direction and spread mode.
Use **Toggle finished** for explicit completion; reaching the last page does not
mark a chapter finished automatically. Search by title/path, filter by reading or
availability status, and sort by title, last read or date added. Covers are loaded
in the background for at most 12 cards on the current shelf page.

**Rename** changes the library title only. **Remove from library** keeps files,
recent history, progress and completion marks. Adding the same folder again restores
its existing identity and adds any newly selected chapters without duplicating them.
**Refresh** checks availability and reading progress; use Add folder again to discover
new chapters. Unavailable chapters remain listed with their saved progress. For now,
restore their original path and refresh; attaching a moved folder is not implemented.
Application reset in this version also clears the library, after confirmation, without deleting books.
The additive library tables use their own schema version (1). Reader schema 2 remains
compatible with Yonaka 0.9.0: older readers can still save settings, history and progress
without touching the library. Reset in an older reader clears only the data it knows
about; use this version to reset the library as well. The short-lived development
schema 3 is recognized by its structure and converted transactionally, preserving data;
unrecognized future schemas are never downgraded.

## Settings

Open **Options → Settings…** (Command+, on macOS, Ctrl+, elsewhere).
**Apply** saves changes; **OK** saves and closes; **Cancel** discards unapplied edits.

- **Appearance:** Ayu Light, Ayu Dark, or System. System appearance is checked in the
  background every five seconds on macOS, Windows and GNOME/Ubuntu. If detection is
  unavailable, the last resolved theme is retained. The Theme menu also supports following the system appearance.
- **Reading:** default direction, single/two-page mode and scale for newly opened books,
  plus thumbnail visibility. Existing books retain saved direction and spread mode;
  these defaults do not overwrite their progress. Original size starts at 100%.
- **Library:** remember a folder and explicitly open the existing import preview with
  **Scan / refresh…**. Apply alone does not scan. The preview includes the selected directory and its immediate subfolders.
- **Data:** show/open the active folder or explicitly change it. An empty target receives
  the current profile; an existing SQLite profile is opened separately without merging.
  The reader closes after switching; launch it again. Books are never moved.

## Appearance and platform behavior

- Options → Theme offers System, Ayu Dark and Ayu Light.
- Both application themes use the [Ayu palette](https://github.com/ayu-theme/ayu-colors), implemented with FlatLaf.
- macOS uses the global screen menu and Command shortcuts.
- Windows and Linux use a menu inside the window, Control shortcuts, and native window decorations. Windows retains its system window controls and Alt+F4 behavior.
- The native title bar and macOS global menu follow the OS appearance; the theme selector colors the application content.
- Theme and scale preferences persist between launches in the local SQLite database.

## Known limitations

- Image rotation is not supported.

## Interface language

Choose a bundled or locally installed translation in **Options → Settings → Appearance → Language**. Apply changes the interface immediately and remembers your choice. Use **Settings → Translations** to create, edit, import or export a catalog. See the [translation guide](translations.md).
