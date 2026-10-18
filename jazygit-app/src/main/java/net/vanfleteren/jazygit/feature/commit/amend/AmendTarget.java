package net.vanfleteren.jazygit.feature.commit.amend;

import net.vanfleteren.jazygit.git.model.Sha;

/**
 * The commit that is amended.
 */
public sealed interface AmendTarget {

    /**
     * HEAD, which is amended in place: also while something is in progress, and also when it is a merge.
     */
    record LastCommit() implements AmendTarget {}

    /**
     * A commit below HEAD, which is amended by rebasing the commits after it; it is not a merge.
     */
    record Older(Sha sha) implements AmendTarget {}
}
