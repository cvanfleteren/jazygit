package net.vanfleteren.jazygit.features.github.show;

import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.kernel.state.Phrase;
import net.vanfleteren.jazygit.kernel.state.operation.Notice;

/**
 * What GitHub did with a merge that is not done yet: no failure, as it merges the pull request later, or may still.
 */
public sealed interface MergeNotice extends Notice.Phrased {

    /**
     * GitHub added the pull request of {@code branch} to the merge queue of its base branch, which merges it later.
     */
    record Enqueued(BranchName branch) implements MergeNotice {

        @Override
        public Phrase phrase() {
            return Phrase.of("github.notice.merge.enqueued", branch.value());
        }
    }

    /**
     * GitHub was still merging the pull request of {@code branch} after so long that it is no longer asked about.
     */
    record StillPending(BranchName branch) implements MergeNotice {

        @Override
        public Phrase phrase() {
            return Phrase.of("github.notice.merge.stillPending", branch.value());
        }
    }
}
