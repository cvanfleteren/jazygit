package net.vanfleteren.jazygit.state;

/**
 * What has the focus: a pane of the left column, an area of the content panel, or an element aside from them, of a
 * popup or the command log. TamboUI knows the focus by the id of the element that has it; the app knows it as this,
 * which says what kind of element that is. The app turns the id into this once, where TamboUI hands it over, and back
 * into the id, {@link #id()}, only where it tells TamboUI where the focus goes.
 */
public sealed interface Focus permits Focus.OnScreen, Focus.Aside {

    /**
     * The id of the element that has the focus, as TamboUI knows it.
     */
    String id();

    /**
     * A focus the screen shown keeps, outside the popups and the command log: a {@link PaneId pane}, or an
     * {@link AreaId area} of the content panel. A closing popup gives the focus back to it, and the screen gets it
     * back when it is shown again.
     */
    sealed interface OnScreen extends Focus permits PaneId, AreaId {}

    /**
     * The element {@code id} has the focus aside from the panes and the content panel: an element of a popup, or the
     * command log (ADR 129). The screen keeps where the focus was before.
     */
    record Aside(String id) implements Focus {}
}
