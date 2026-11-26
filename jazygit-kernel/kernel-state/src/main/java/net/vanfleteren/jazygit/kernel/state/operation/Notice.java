package net.vanfleteren.jazygit.kernel.state.operation;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.InProgress;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.Phrase;

/**
 * What the user is told about how the last operation went, when that is no failure but still needs their
 * attention, as data; the view turns it into text.
 */
public sealed interface Notice {

    /**
     * A notice of a feature, one of a sealed set of its own, as its errors are: the kernel only knows that it says
     * itself as a phrase, so adding one changes nothing outside the feature (ADR 169).
     */
    non-sealed interface Phrased extends Notice {

        Phrase phrase();
    }

    /**
     * The operation stopped on conflicts, which the user resolves before going on.
     *
     * @param inProgress what the repository is in the middle of now; none for a squash merge, which the user
     *                   commits once the conflicts are resolved
     * @param conflicts  how many files have conflicts
     */
    record StoppedOnConflicts(Option<InProgress> inProgress, int conflicts) implements Notice {}

    /**
     * Moving the commits of {@code branch} since {@code oldest} to {@code target}, a new or an existing branch,
     * stopped on conflicts while copying them onto it: {@code branch} still has them, until the user moves it back.
     */
    record MoveStopped(StoppedOnConflicts stopped, BranchName branch, Sha oldest, BranchName target)
            implements Notice {}
}
