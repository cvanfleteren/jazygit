package net.vanfleteren.jazygit.kernel.state;

/**
 * The id of a pane of the left column of a screen, which is also the id of the scope of its keys and of the element
 * that draws it, which can have the focus: it is then the {@link Focus}.
 *
 * @param value the id
 */
public record PaneId(String value) implements Focus.OnScreen {

    /**
     * The pane of the commits of HEAD, or of what a search of them found, which lives in a group of features: the
     * content panel and the focus name it by this id (ADR 158).
     */
    public static final PaneId COMMITS = new PaneId("commits");

    /**
     * The pane of the stashes, which lives in a group of features: the content panel and the focus name it by this id
     * (ADR 158).
     */
    public static final PaneId STASH = new PaneId("stash");

    @Override
    public String id() {
        return value;
    }
}
