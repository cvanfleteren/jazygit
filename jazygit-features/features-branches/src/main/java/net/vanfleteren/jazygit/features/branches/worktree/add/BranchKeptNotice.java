package net.vanfleteren.jazygit.features.branches.worktree.add;

import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.kernel.state.Phrase;
import net.vanfleteren.jazygit.kernel.state.operation.Notice;

/**
 * {@code branch}, which a new worktree checked out, was kept as it was: it has {@code ahead} commits that are not on
 * {@code upstream}, the remote branch it follows, so it was not fast-forwarded to it (ADR 109).
 */
public record BranchKeptNotice(BranchName branch, int ahead, RemoteBranch upstream) implements Notice.Phrased {

    @Override
    public Phrase phrase() {
        return Phrase.of(
                "branches.notice.branchKept",
                Phrase.verbatim(branch.value()),
                Phrase.count(ahead),
                Phrase.verbatim(upstream.shortName()));
    }
}
