package net.vanfleteren.jazygit.kernel.state;

import net.vanfleteren.jazygit.kernel.state.diff.Changes;
import net.vanfleteren.jazygit.kernel.state.preferences.Screen;

/**
 * What happened in the kernel that a feature may answer (ADR 158): a part it keeps answers the events while it is
 * kept, and a {@link Reactions.Reaction} it declares answers them always. An event is added when the first feature
 * needs it, and says what happened without naming a forge: what a feature does about it is its own.
 */
public sealed interface KernelEvent {

    /**
     * The log of HEAD was read again, as HEAD moved: what is read from the history of HEAD changed with it.
     */
    record LogRead() implements KernelEvent {}

    /**
     * An operation on the repository is done, and the repository is read again for it.
     *
     * @param changedForge whether it changed what the repository's forge has, as a push or a change to a pull request
     *                     does: what the forge says is then to be read again
     */
    record OperationDone(boolean changedForge) implements KernelEvent {}

    /**
     * A fetch from the remotes is done, whether or not it brought anything: what the remotes and their forge have
     * changes on its own.
     */
    record Fetched() implements KernelEvent {}

    /**
     * A repository was opened, at the start of the app or once another was switched to: nothing was read of it yet.
     */
    record RepositoryOpened() implements KernelEvent {}

    /**
     * The user switched to {@code screen}, which was not shown: what it shows may be read again (ADR 168).
     */
    record ScreenShown(Screen screen) implements KernelEvent {}

    /**
     * The files of {@code changes} were opened to be listed, and are being read: what goes with them, as the remarks a
     * forge has on them, may be read too (ADR 171). The kernel names no forge here: {@code changes} may be of a pull
     * request, as it may be of a commit, but who has remarks on it is for the features to know.
     */
    record ChangesOpened(Changes changes) implements KernelEvent {}

    /**
     * The feature {@code feature} was switched on for the repository when {@code on}, else off, as it found out
     * whether it applies (ADR 171): once on, it reads what it shows.
     */
    record Switched(String feature, boolean on) implements KernelEvent {}
}
