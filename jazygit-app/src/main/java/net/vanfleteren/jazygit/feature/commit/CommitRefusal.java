package net.vanfleteren.jazygit.feature.commit;

import net.vanfleteren.jazygit.state.AppError;

/**
 * What the commit features refuse up front.
 */
public sealed interface CommitRefusal extends AppError.Refusal {

    /**
     * A commit was asked for, but nothing has changed.
     */
    record NothingToCommit() implements CommitRefusal {
        @Override
        public String key() {
            return "error.commit.nothing";
        }
    }

    /**
     * Amending was asked for, but nothing has changed to amend the commit with.
     */
    record NothingToAmend() implements CommitRefusal {
        @Override
        public String key() {
            return "error.amend.nothing";
        }
    }

    /**
     * Amending a merge commit was asked for, which is not possible.
     */
    record MergeNotAmendable() implements CommitRefusal {
        @Override
        public String key() {
            return "error.amendCommit.merge";
        }
    }
}
