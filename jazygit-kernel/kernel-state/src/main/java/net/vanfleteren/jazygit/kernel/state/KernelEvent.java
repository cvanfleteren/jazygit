package net.vanfleteren.jazygit.kernel.state;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.RepoPath;
import net.vanfleteren.jazygit.kernel.state.diff.Changes;
import net.vanfleteren.jazygit.kernel.state.edit.EditedFile;
import net.vanfleteren.jazygit.kernel.state.preferences.Screen;
import net.vanfleteren.jazygit.kernel.state.view.TabId;

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
    record Switched(FeatureId feature, boolean on) implements KernelEvent {}

    /**
     * The user had {@code pane} show its tab {@code tab}, which it did not show (ADR 185, 186): what the tab lists is
     * read while it is shown.
     */
    record TabShown(PaneId pane, TabId tab) implements KernelEvent {}

    /**
     * A feature asked for {@code file} to be opened in an editor (ADR 206): the feature that opens files
     * answers it, with the editors the user set, so the one that asks names none. A popup that is open stays under the
     * menu of the editors, and comes back once it closes, when it is {@link
     * net.vanfleteren.jazygit.kernel.state.popup.Popup#coverable() coverable}; else the first editor opens it, as the
     * menu would take that popup away.
     *
     * @param file  the file, of the working tree or elsewhere on disk
     * @param line  the line it opens at, counted from 1; none to open it as {@code e} does, at its first conflict when it
     *              is in conflict, else at its start
     * @param title what the menu of the editors is titled, if it asks, when it is not named by the file
     */
    record FileToOpen(EditedFile file, Option<Integer> line, Option<Phrase> title) implements KernelEvent {

        /**
         * The file at {@code path} in the working tree to be opened as {@code e} opens it.
         */
        public static FileToOpen of(RepoPath path) {
            return new FileToOpen(EditedFile.of(path), Option.none(), Option.none());
        }

        /**
         * The file at {@code path} in the working tree to be opened at {@code line}.
         */
        public static FileToOpen at(RepoPath path, int line) {
            return new FileToOpen(EditedFile.of(path), Option.some(line), Option.none());
        }

        /**
         * {@code file} to be opened at {@code line}.
         */
        public static FileToOpen at(EditedFile file, int line) {
            return new FileToOpen(file, Option.some(line), Option.none());
        }

        /**
         * This, with the menu of the editors titled {@code title}.
         */
        public FileToOpen titled(Phrase title) {
            return new FileToOpen(file, line, Option.some(title));
        }
    }
}
