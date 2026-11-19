package net.vanfleteren.jazygit.git.model;

import io.vavr.control.Option;

/**
 * Where a new branch starts, and whether it follows what it starts at (ADR 40).
 */
public sealed interface NewBranchStart {

    /**
     * What git is given to start the branch at.
     */
    Revision start();

    /**
     * At {@code start}; whether it follows it is up to git's {@code branch.autoSetupMerge}, as for
     * {@code git branch <name> <start>}.
     *
     * @param parent the local branch it is made from, recorded as such, if it is made from one
     */
    record From(Revision start, Option<BranchName> parent) implements NewBranchStart {}

    /**
     * At the remote branch {@code upstream}, which it follows, as checking that out does.
     */
    record Following(RemoteBranch upstream) implements NewBranchStart {

        @Override
        public Revision start() {
            return upstream.tracking();
        }
    }
}
