# 118. Left held down stops at the start of the lines

Date: 2026-11-13

## Context

In a diff, Left and `h` scroll sideways. Once the diff is scrolled all the way left, Left leaves it for the pane it
belongs to, as Escape does, and in a tree pane Left collapses the folder. Holding Left down to scroll back to the start
of the lines therefore scrolls there, leaves the diff, and goes on collapsing the tree with the keys the terminal
repeats.

A terminal sends a key held down as the same key over and over. By default TamboUI asks a terminal that speaks the kitty
keyboard protocol for the enhancements that leave typing as it is (disambiguation and alternate keys), and then a
repeated key reads as a press. One more flag, "report event types", makes the terminal mark a repeated key as a repeat,
and also send a key that is let go, as `ESC [ 1;1:3D` for Left. TamboUI reads both into the kind of a `KeyEvent`.
TamboUI's key bindings ignore a key let go, but jazygit's dialogs also compare the key code itself
(`event.code() == KeyCode.DOWN`), which a key let go matches as well.

## Decision

- jazygit asks the terminal for TamboUI's default enhancements plus "report event types". `HandOverBackend` does this
  when the app starts, and again when it takes the terminal back from an editor.
- `HandOverBackend` takes a key let go out of what the terminal sends before TamboUI reads it, as it takes out the
  reports of the focus. No handler sees one, so none has to tell it from a press. Not: checking for a key let go in
  every handler that compares key codes.
- A key let go does not tell that the terminal has the focus again, unlike a key pressed. A terminal may send one
  for a key let go just after it lost the focus, such as the Alt of Alt+Tab.
- In a diff scrolled all the way left, a repeated Left does nothing. Only Left pressed again leaves the diff. Left
  pressed while the diff already starts at the start of the lines leaves it at once, as before.
- Not: telling a held key by the time between keys, which works in every terminal but has to guess the rate at which
  keys repeat.

## Consequences

- In kitty, WezTerm, foot, Ghostty and Konsole, holding Left stops at the start of the lines.
- A terminal without the protocol, and tmux, ignore the flag and send a held key as presses: there, holding Left
  leaves the diff as before.
- Every other key held down arrives as a repeat. TamboUI's bindings and jazygit's handlers treat a repeat as a press,
  so nothing else changes.
