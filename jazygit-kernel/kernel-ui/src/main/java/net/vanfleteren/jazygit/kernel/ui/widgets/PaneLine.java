package net.vanfleteren.jazygit.kernel.ui.widgets;

import java.util.function.Function;
import net.vanfleteren.jazygit.kernel.state.Msg;

/**
 * The line a feature opens above the list of a pane, to type in (ADR 172): the pane draws it as a {@link FilterLine},
 * and while it is open, gives it the keys that edit it, Enter and Escape, and what is pasted. The pane knows not what
 * the line is for.
 *
 * @param text      what the line holds
 * @param edited    what to send when the user changed its text to the one given
 * @param submitted what to send when the user pressed Enter
 * @param cancelled what to send when the user pressed Escape
 */
public record PaneLine(String text, Function<String, Msg> edited, Msg submitted, Msg cancelled) {}
