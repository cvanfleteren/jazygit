# TamboUI

What TamboUI does that jazygit relies on, learned by testing, reading the source or docs, or measuring. Each fact says
when it was learned, so a fact that has grown old can be checked again; see README.md.

## State and focus

- TamboUI is retained-mode: its lists, trees and text inputs keep mutable state of their own (selected index, expanded
  nodes, text buffer with caret), and focus lives in its `FocusManager`. *Why it matters:* view-only state stays in
  widgets; whatever an update reads must be mirrored into the Model. — learned 2026-11-22 · [ADR
  0002](../adr/0002-model-and-view-state.md)
- In TamboUI, focus is view state kept by TamboUI itself (its `FocusManager`, which jazygit's `FocusKeeper` wraps), not
  in the model. *Why it matters:* the model keeps the pane that had the focus last on its own (`Model.shownPane`); the
  command log's focus is kept aside, like a popup's, and is not reported to the model. — learned 2026-11-22 · [ADR
  0092](../adr/0092-resizing-and-maximizing-panes-with-keys.md), [ADR
  0129](../adr/0129-the-command-log-takes-the-focus.md)
- A click on something that takes no focus (like the command log) makes TamboUI clear the focus and handle nothing;
  TamboUI also auto-focuses after a frame. *Why it matters:* the app must restore focus and ask for a frame after
  presses and focus moves. — learned 2026-11-22 · [ADR 0014](../adr/0014-redraw-on-change.md)
- TamboUI's router takes Tab to move the focus before any element sees it, and TamboUI has no `:focus-within` for a box
  to show the focus of what is in it. *Why it matters:* the verdict choice is one focus stop with arrows, not a
  Tab-switched toggle. — learned 2026-11-22 · [ADR 0100](../adr/0100-approving-or-asking-for-changes.md)
- TamboUI's own Tab order goes round files, branches, commits and stash, so from the diff the files are reached through
  the stash, and one more Tab focuses the branches. *Why it matters:* this lost what Enter had opened in the files pane
  within two keys, which is why Enter locks the files pane to its list. — learned 2026-11-22 · [ADR
  0149](../adr/0149-enter-locks-the-files-to-a-list.md)

## Frames and ticks

- TamboUI draws a frame after every handled event, after a resize, and on every tick; work queued via
  `runOnRenderThread` or `runLater` runs but is only drawn on the next tick. *Why it matters:* without ticks, changes
  arriving from commands draw nothing unless a frame is requested. — learned 2026-11-22 · [ADR
  0008](../adr/0008-resize-left-column.md), [ADR 0014](../adr/0014-redraw-on-change.md)
- TamboUI sends a tick every 40 ms by default and draws the whole screen after each; `noTick()` turns it off, and
  resizes still draw, as TamboUI checks for them itself. *Why it matters:* the default costs 25 frames a second when
  idle. — learned 2026-11-22 · [ADR 0014](../adr/0014-redraw-on-change.md)
- TamboUI ticks, when on, draw 25 frames a second whether anything moves or not, and its effects only change the cells
  of a frame already drawn, not the layout. Its layout solver turns a percent into columns with its own rounding. *Why
  it matters:* the column slide asks for its own frames and lays out in whole columns. — learned 2026-11-22 · [ADR
  0081](../adr/0081-the-left-column-slides-to-its-width.md)
- An idle app drawing at TamboUI's default 25 fps took about a quarter of a core on the render thread, some 10 ms a
  frame, a third of it in TamboUI's CSS: `StyleEngine.resolve` copies all rules and variables for every element it
  styles. *Why it matters:* frames are drawn only on change; caching in `StyleEngine` is a TamboUI to-do. — learned
  2026-11-22 · [ADR 0014](../adr/0014-redraw-on-change.md)
- TamboUI has no call to request a frame; a custom event queued through `TuiRunner.dispatch` and handled by a global
  handler draws one. A fake `TickEvent` also works, but TamboUI always draws on a tick, so a handled key would draw
  twice. *Why it matters:* jazygit uses its own `Redraw` event. — learned 2026-11-22 · [ADR
  0014](../adr/0014-redraw-on-change.md)

## Input

- TamboUI hands every mouse event to the global handlers first, and both backends always enable button-event tracking
  (`?1002h`), so moving with a button held arrives as `DRAG` events without enabling motion tracking. *Why it matters:*
  the column border can be dragged with a global handler. — learned 2026-11-22 · [ADR
  0008](../adr/0008-resize-left-column.md)
- TamboUI reads the focus reports `ESC [ I` and `ESC [ O` as one unknown key. *Why it matters:* `HandOverBackend`
  filters them out before TamboUI sees them and dispatches a focus message. — learned 2026-11-22 · [ADR
  0058](../adr/0058-less-reading-in-the-background.md)
- By default TamboUI asks terminals for the kitty keyboard enhancements that leave typing as it is (disambiguation and
  alternate keys); it reads repeat and release into the kind of a `KeyEvent`, and its key bindings ignore releases. A
  comparison of `event.code()` alone still matches a release. *Why it matters:* `HandOverBackend` strips releases before
  TamboUI reads them, so no dialog acts on a release. — learned 2026-11-22 · [ADR
  0118](../adr/0118-left-held-down-stops-at-the-start-of-the-lines.md)
- Each key arrives as an event of TamboUI's terminal backend, which has its own buffers. *Why it matters:* jazygit's
  clearing of the passphrase cannot reach those copies. — learned 2026-11-22 · [ADR
  0150](../adr/0150-ssh-asks-jazygit-for-a-passphrase.md)
- TamboUI delivers a paste as a `PasteEvent` whose text is a `String`. *Why it matters:* a passphrase must not arrive
  that way, so bracketed paste is turned off while the popup is open. — learned 2026-11-22 · [ADR
  0150](../adr/0150-ssh-asks-jazygit-for-a-passphrase.md)
- TamboUI's text input keeps its text itself, as a `String`, and nothing masks what is typed. *Why it matters:* the
  passphrase popup needs an input element of jazygit's own that edits a `char[]` and draws `•`. — learned 2026-11-22 ·
  [ADR 0150](../adr/0150-ssh-asks-jazygit-for-a-passphrase.md)
- TamboUI 0.6.0 can neither select text nor copy it. *Why it matters:* copying is done by jazygit with OSC 52. — learned
  2026-11-22 · [ADR 0094](../adr/0094-copying-lines-of-a-diff.md) · TamboUI 0.6.0

## Widgets and layout

- TamboUI counts ambiguous-width characters such as `●`, `≡` and `◆` as one column. *Why it matters:* layouts computed
  by TamboUI are one column off in ambiguous-wide terminals. — learned 2026-11-22 · [ADR
  0045](../adr/0045-two-lines-per-branch.md), [ADR 0048](../adr/0048-one-set-of-pull-request-symbols.md)
- TamboUI's text area measures the width of every line it is given on every render, to decide whether it scrolls
  sideways. `Line.width()` is not cached and walks every character of every span. `Line` and `Span` are final, so an app
  cannot cache their widths. *Why it matters:* a frame cost as much as the diff was long, so only the visible lines are
  given real content. — learned 2026-11-22 · [ADR 0078](../adr/0078-an-area-is-given-only-the-lines-that-show.md)
- TamboUI's text area only learns its size while it draws, and whether it keeps a column for its scrollbar is TamboUI's
  choice. TamboUI registers an element and its id once per frame. *Why it matters:* when the size changes, the area is
  drawn twice in one frame to fill it to the edge. — learned 2026-11-22 · [ADR
  0080](../adr/0080-a-changed-line-goes-on-to-the-edge-of-the-area.md)
- A wrapper around an area is not an element that TamboUI styles. *Why it matters:* the content panel is a column around
  its areas. — learned 2026-11-22 · [ADR 0080](../adr/0080-a-changed-line-goes-on-to-the-edge-of-the-area.md)
- TamboUI's text area scrolls by lines, so a line it wraps by itself scrolls as one unit. *Why it matters:* the command
  log pre-wraps its lines (`TextWidth.wrapped`) as a word-wrapping `Paragraph` would. — learned 2026-11-22 · [ADR
  0129](../adr/0129-the-command-log-takes-the-focus.md)
- A TamboUI list measures the text of every row on every frame unless the rows report their height. With 10 000 rows
  this made each key press in the commits pane take 70-230 ms. *Why it matters:* list rows declare their height. —
  learned 2026-11-22 · [ADR 0130](../adr/0130-the-log-reads-3000-commits.md)
- TamboUI's `TreeElement` folds, scrolls and selects by itself, draws its guides before what a row holds (so a change
  mark lands after the guides rather than in a gutter), and does not scroll sideways. *Why it matters:* rendered
  JSON/YAML are drawn as rows of jazygit's own rather than with `TreeElement`. — learned 2026-11-22 · [ADR
  0122](../adr/0122-a-version-of-json-rendered.md)
- TamboUI's `GuideStyle.UNICODE` draws tree guides as `├──`, `└──` and `│`. *Why it matters:* rendered JSON/YAML copy
  these guides so they look like the files tree. — learned 2026-11-22 · [ADR
  0122](../adr/0122-a-version-of-json-rendered.md)
- TamboUI has a `LineGauge` widget. *Why it matters:* it was rejected for the checks bar, which is drawn as text rows
  that tests read. — learned 2026-11-22 · [ADR 0089](../adr/0089-how-far-the-checks-are.md)
- `tamboui-image` draws images with the Kitty, iTerm2 or Sixel protocol where the terminal supports one, otherwise in
  half blocks or braille. It reads images with AWT's `ImageIO`. *Why it matters:* it cannot be used in jazygit's native
  image. — learned 2026-11-22 · [ADR 0082](../adr/0082-an-image-in-a-comment-is-a-placeholder.md), [ADR
  0084](../adr/0084-an-image-in-a-review-remark-is-drawn-in-half-blocks.md)

## Markdown

- `tamboui-toolkit-markdown` (in the TamboUI BOM) renders headings, lists, quotes, code blocks with syntax highlighting,
  tables and links, and measures the rendered height so it can be scrolled to its end; other HTML shows as dimmed text.
  *Why it matters:* pull request comments render as Markdown in the terminal. — learned 2026-11-22 · [ADR
  0006](../adr/0006-view-pull-request-comments.md)
- TamboUI parses Markdown with commonmark-java, with tables, strikethrough and task lists. It prints raw HTML as
  written, has no front matter support, draws an image as `[image: alt](url)`, highlights code blocks with its own
  replaceable highlighter, and styles Markdown only by element kind, not block by block. *Why it matters:* rendered
  Markdown is drawn block by block with `MarkdownView`, using jazygit's lexer as the highlighter. — learned 2026-11-22 ·
  [ADR 0103](../adr/0103-a-version-of-markdown-rendered.md)
- TamboUI draws Markdown as a widget (`MarkdownView`) or an element (`MarkdownElement`); its layout into lines is
  internal, and a code block or table is laid out as a widget, not as lines. *Why it matters:* review comments are
  rendered by drawing `MarkdownView` into an off-screen buffer and reading its cells back as styled lines, using public
  classes only. — learned 2026-11-22 · [ADR 0055](../adr/0055-review-remarks-in-a-pull-requests-diff.md)
- TamboUI's Markdown table gives every column an equal share of the width and cuts off what doesn't fit. *Why it
  matters:* jazygit draws tables itself. — learned 2026-11-22 · [ADR
  0103](../adr/0103-a-version-of-markdown-rendered.md)
- TamboUI's `MarkdownElement` shows only the text of a link `[text](url)`: it puts the URL on the style of each cell as
  a `Hyperlink`, and the backend writes it as OSC 8. *Why it matters:* the link is not visible, so the positions of
  links are kept to make them clickable. — learned 2026-11-22 · [ADR
  0096](../adr/0096-opening-the-links-of-a-comment.md)
- TamboUI draws a Markdown image as `[image: alt](url)` with the full URL, and an `<img>` tag as it is written, which
  for a bot's status icon took three lines after one line of text. *Why it matters:* images are replaced with a short
  placeholder before TamboUI draws the Markdown. — learned 2026-11-22 · [ADR
  0082](../adr/0082-an-image-in-a-comment-is-a-placeholder.md)

## Images

- TamboUI keeps a combining character in the cell of the character before it only for some of them; the first 100
  of Kitty's row and column diacritics are among them. *Why it matters:* a placeholder of an image uses no others, so
  an id's highest byte is less than 100. — learned 2026-11-06 · [ADR
  0191](../adr/0191-the-terminal-draws-a-picture-sharp-where-it-can.md)
- TamboUI writes only the cells of a frame that changed, and has no hook after them; drawing a widget with raw output
  has it write the frame in a synchronized update even when no cell changed. *Why it matters:* Sixel's images are
  written by the backend before the synchronized update ends. — learned 2026-11-06 · [ADR
  0191](../adr/0191-the-terminal-draws-a-picture-sharp-where-it-can.md)
