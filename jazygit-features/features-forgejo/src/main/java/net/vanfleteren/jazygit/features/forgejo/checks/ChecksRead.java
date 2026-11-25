package net.vanfleteren.jazygit.features.forgejo.checks;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.core.forgejo.CommitStatus;
import net.vanfleteren.jazygit.core.forgejo.ForgejoCheck;
import net.vanfleteren.jazygit.core.repo.Sha;

/**
 * The checks read of the tip of a branch on Forgejo (ADR 195).
 *
 * @param repository the repository they are of, as {@code owner/name}
 * @param sha        the commit they are of
 * @param checks     the checks, in the order their contexts were first posted
 */
public record ChecksRead(String repository, Sha sha, List<ForgejoCheck> checks) {

    /**
     * The checks in the order they are listed: the failed ones first, then the running ones, the queued ones and the
     * passed ones, each in the order they were first posted.
     */
    public List<ForgejoCheck> ordered() {
        return checks.sortBy(ChecksRead::rank);
    }

    private static int rank(ForgejoCheck check) {
        return check.state() == CommitStatus.State.FAILED ? 0 : !check.done() ? (check.queued() ? 2 : 1) : 3;
    }

    /**
     * How far they are together, from 0 to 1: how many of them are done, as Forgejo does not tell how long they take.
     */
    public double progress() {
        return checks.isEmpty() ? 1 : (double) checks.count(ForgejoCheck::done) / checks.size();
    }
}
