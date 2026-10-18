package net.vanfleteren.jazygit.state;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.InProgress;
import net.vanfleteren.jazygit.git.model.Sha;

/**
 * What the user is told about how the last operation went, when that is no failure but still needs their
 * attention, as data; the view turns it into text.
 */
public sealed interface Notice {

    /**
     * The operation stopped on conflicts, which the user resolves before going on.
     *
     * @param inProgress what the repository is in the middle of now; none for a squash merge, which the user
     *                   commits once the conflicts are resolved
     * @param conflicts  how many files have conflicts
     */
    record StoppedOnConflicts(Option<InProgress> inProgress, int conflicts) implements Notice {}

    /**
     * Moving the commits of {@code branch} since {@code oldest} to a new branch stopped on conflicts while copying
     * them onto it: {@code branch} still has them, until the user moves it back.
     */
    record MoveStopped(StoppedOnConflicts stopped, Branch.Name branch, Sha oldest) implements Notice {}
}
