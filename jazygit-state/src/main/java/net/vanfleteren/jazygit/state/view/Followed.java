package net.vanfleteren.jazygit.state.view;

import net.vanfleteren.jazygit.state.diff.Changes;

/**
 * What the files pane lists for the pane with the focus, which follows its highlight (ADR 141).
 */
public sealed interface Followed {

    /**
     * The files of {@code of}: the commit, the stash or the branch highlighted.
     */
    record Files(Changes of) implements Followed {}

    /**
     * Nothing: the highlighted row has no files to list, for the reason {@code why}, which the pane says in their place.
     */
    record Nothing(Unlisted why) implements Followed {}

    /**
     * The files of the working tree: the pane with the focus follows nothing.
     */
    record WorkingTree() implements Followed {}
}
