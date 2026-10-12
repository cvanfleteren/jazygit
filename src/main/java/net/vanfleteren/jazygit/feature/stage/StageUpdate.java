package net.vanfleteren.jazygit.feature.stage;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.stage.StageCmd.Stage;
import net.vanfleteren.jazygit.feature.stage.StageCmd.Unstage;
import net.vanfleteren.jazygit.feature.stage.StageMsg.Requested;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.state.Model;
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
        List<String> unstaged = files.filter(FileEntry::unstaged).map(FileEntry::path).toList();
        if (!unstaged.isEmpty()) {
            // Adding a directory stages every change below it, which are exactly the changed files that
            // are not staged completely yet.
            return Next.of(model.clearError(),
                    new Stage(directory.map(d -> List.of(pathspec(d))).getOrElse(unstaged)));
        }
        List<FileEntry> staged = files.filter(FileEntry::staged).toList();
        if (staged.isEmpty()) {
            return Next.of(model);
        }
        List<String> added = staged.filter(f -> f.type() == ChangeType.ADDED).map(FileEntry::path).toList();
        List<String> others = staged.filter(f -> f.type() != ChangeType.ADDED).map(FileEntry::path).toList();
        // Resetting a directory takes every staged change below it out of the index. That is only right
        // when none of them is a newly added file, which has to be removed from the index instead: doing
        // that for the directory would remove all its tracked files.
        if (directory.isDefined() && added.isEmpty()) {
            return Next.of(model.clearError(), new Unstage(List.of(), List.of(pathspec(directory.get()))));
        }
        return Next.of(model.clearError(), new Unstage(added, others));
    }

    /**
     * The repository root has the empty path; git calls it {@code .}.
     */
    private static String pathspec(String directory) {
        return directory.isEmpty() ? "." : directory;
    }
}
