package net.vanfleteren.jazygit.kernel.ui.content;

import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;

/**
 * What the keys of a {@link Body} of its own are handled with, beyond those of its cursor.
 *
 * @param keymap    the keys of the app, which a body looks its own up in
 * @param searching whether a search of the content panel is kept in its area: {@code n} and {@code N} then go to its
 *                  matches, which the area does itself (ADR 126)
 */
public record KeyContext(Keymap keymap, boolean searching) {}
