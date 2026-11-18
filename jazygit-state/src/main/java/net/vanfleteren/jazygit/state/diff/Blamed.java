package net.vanfleteren.jazygit.state.diff;

import io.vavr.collection.HashSet;
import io.vavr.collection.Set;
import net.vanfleteren.jazygit.git.model.Blame;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.Loadable;

/**
 * The blame a version shown blamed is drawn with (ADR 147): loading while it is read, then read. One that failed is
 * not drawn.
 *
 * @param blame the blame of the version, or that it is still read
 * @param own   the commits whose version this is, which its lines changed by them are tinted as already: a commit, or
 *              the three of a stash
 */
public record Blamed(Loadable<Blame> blame, Set<Sha> own) {

    /**
     * {@code blame}, of no commit of its own: a version of the working tree, or of a branch.
     */
    public Blamed(Loadable<Blame> blame) {
        this(blame, HashSet.empty());
    }
}
