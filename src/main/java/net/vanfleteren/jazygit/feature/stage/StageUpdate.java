package net.vanfleteren.jazygit.feature.stage;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.stage.StageCmd.Stage;
import net.vanfleteren.jazygit.feature.stage.StageCmd.Unstage;
import net.vanfleteren.jazygit.feature.stage.StageMsg.Requested;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Staging and unstaging files.
 */
public final class StageUpdate {

    private StageUpdate() {
    }

    public static Next update(Model model, StageMsg msg) {
        return switch (msg) {
            case Requested(List<FileEntry> files, Option<String> directory) -> toggle(model, files, directory);
        };
    }

    /**
     * Stages the files that have unstaged changes; when there are none, unstages them all. For a selected
     * directory, git gets the directory itself where that has the same effect.
     */
    private static Next toggle(Model model, List<FileEntry> files, Option<String> directory) {
        List<String> unstaged = files.filter(FileEntry::unstaged).flatMap(f -> f.paths(false));
        List<FileEntry> staged = files.filter(FileEntry::staged);
        // Adding a directory stages every change below it, which are exactly the changed files that are not
        // staged completely yet.
        return !unstaged.isEmpty()
                ? Update.start(model.clearError(), new Stage(directory.map(d -> List.of(pathspec(d))).getOrElse(unstaged)))
                : staged.isEmpty() ? Next.of(model)
                : Update.start(model.clearError(), unstage(staged, directory));
    }

    private static Unstage unstage(List<FileEntry> staged, Option<String> directory) {
        // A renamed file is new at its path, and its origin is reset to HEAD to bring it back.
        List<String> added = FileEntry.paths(staged, FileEntry::isNew);
        List<String> others = FileEntry.paths(staged, f -> !f.isNew())
                .appendAll(staged.flatMap(FileEntry::origin));
        // Resetting a directory takes every staged change below it out of the index. That is only right
        // when none of them is a newly added file, which has to be removed from the index instead: doing
        // that for the directory would remove all its tracked files. A rename may also come from outside it.
        return directory.filter(d -> added.isEmpty())
                .map(d -> new Unstage(List.of(), List.of(pathspec(d))))
                .getOrElse(() -> new Unstage(added, others));
    }

    /**
     * The repository root has the empty path; git calls it {@code .}.
     */
    private static String pathspec(String directory) {
        return directory.isEmpty() ? "." : directory;
    }
}
