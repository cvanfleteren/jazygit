package net.vanfleteren.jazygit.state;

/**
 * The id of a pane of the left column of a screen, which is also the id of the scope of its keys and of the element
 * that draws it, which can have the focus.
 *
 * @param value the id
 */
public record PaneId(String value) {}
