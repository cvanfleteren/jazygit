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
     * {@code branch} follows a branch on Forgejo that no open pull request merges.
     */
    record None(BranchName branch) implements PullRequestNotice {

        @Override
        public Phrase phrase() {
            return Phrase.of("forgejo.notice.noPullRequest", branch.value());
        }
    }

    /**
     * {@code branch} follows no branch of a remote on Forgejo, so no pull request can merge it.
     */
    record NotOnForgejo(BranchName branch) implements PullRequestNotice {

        @Override
        public Phrase phrase() {
            return Phrase.of("forgejo.notice.notOnForgejo", branch.value());
        }
    }
}
