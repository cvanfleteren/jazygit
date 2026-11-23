package net.vanfleteren.jazygit.kernel.state.view;

/**
 * The id of a tab of a pane (ADR 185): an open value, given by the pane for its own tabs and by a feature for those it
 * adds, as it names a part. Its value is shaped as a key of a bundle, but names no text.
 *
 * @param value the id
 */
public record TabId(String value) {}
