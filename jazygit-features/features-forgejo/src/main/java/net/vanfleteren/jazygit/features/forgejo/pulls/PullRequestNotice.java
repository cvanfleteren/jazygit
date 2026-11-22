package net.vanfleteren.jazygit.features.forgejo.pulls;

import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.kernel.state.Phrase;
import net.vanfleteren.jazygit.kernel.state.operation.Notice;

/**
 * What the user is told when a branch has no pull request to show on Forgejo (ADR 176): no failure, but what they
 * asked for is not there.
 */
public sealed interface PullRequestNotice extends Notice.Phrased {

    /**
     * No open pull request of the home merges the branch {@code branch} is pushed to.
     */
    record None(BranchName branch) implements PullRequestNotice {

        @Override
        public Phrase phrase() {
            return Phrase.of("forgejo.notice.noPullRequest", branch.value());
        }
    }
}
