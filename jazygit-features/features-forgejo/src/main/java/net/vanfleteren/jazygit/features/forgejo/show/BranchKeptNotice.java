package net.vanfleteren.jazygit.features.forgejo.show;

import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.kernel.state.Phrase;
import net.vanfleteren.jazygit.kernel.state.operation.Notice;

/**
 * Forgejo merged the pull request of {@code branch}, but kept its head branch, which was to be deleted, for the
 * reason {@code why} it gave (ADR 180): no failure, as it is merged.
 */
public record BranchKeptNotice(BranchName branch, String why) implements Notice.Phrased {

    @Override
    public Phrase phrase() {
        return Phrase.of("forgejo.notice.merge.branchKept", branch.value(), why);
    }
}
