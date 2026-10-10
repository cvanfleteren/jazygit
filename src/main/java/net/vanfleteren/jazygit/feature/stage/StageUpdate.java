package net.vanfleteren.jazygit.feature.stage;

import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.feature.stage.StageMsg.Requested;

import net.vanfleteren.jazygit.feature.stage.StageMsg.Failed;

import net.vanfleteren.jazygit.feature.stage.StageMsg.Done;

import net.vanfleteren.jazygit.feature.stage.StageCmd.Unstage;

import net.vanfleteren.jazygit.feature.stage.StageCmd.Stage;

import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.state.CommandLog;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import java.util.List;
import java.util.Optional;

/**
 * Staging and unstaging files.
 */
public final class StageUpdate {

    private StageUpdate() {
    }

    public static Next update(Model model, StageMsg msg) {
        return switch (msg) {
            case Requested(List<FileEntry> files, Optional<String> directory) -> toggle(model, files, directory);
            case Done(StageMsg.Action action, List<String> commands) -> Update.refresh(CommandLog.append(
                    model.withError(Optional.empty()),
                    action == StageMsg.Action.STAGE ? "log.title.stage" : "log.title.unstage", commands));
            case Failed(String message) -> Update.refresh(model.withError(Optional.of(Messages.get("error.staging.failed", message))));
        };
    }

    /**
     * Stages the files that have unstaged changes; when there are none, unstages them all. For a selected
     * directory, git gets the directory itself where that has the same effect.
     */
    private static Next toggle(Model model, List<FileEntry> files, Optional<String> directory) {
        List<String> unstaged = files.stream().filter(FileEntry::unstaged).map(FileEntry::path).toList();
        if (!unstaged.isEmpty()) {
            // Adding a directory stages every change below it, which are exactly the changed files that
            // are not staged completely yet.
            return Next.of(model.withError(Optional.empty()),
                    new Stage(directory.map(d -> List.of(pathspec(d))).orElse(unstaged)));
        }
        List<FileEntry> staged = files.stream().filter(FileEntry::staged).toList();
        if (staged.isEmpty()) {
            return Next.of(model);
        }
        List<String> added = staged.stream().filter(f -> f.type() == ChangeType.ADDED).map(FileEntry::path).toList();
        List<String> others = staged.stream().filter(f -> f.type() != ChangeType.ADDED).map(FileEntry::path).toList();
        // Resetting a directory takes every staged change below it out of the index. That is only right
        // when none of them is a newly added file, which has to be removed from the index instead: doing
        // that for the directory would remove all its tracked files.
        if (directory.isPresent() && added.isEmpty()) {
            return Next.of(model.withError(Optional.empty()), new Unstage(List.of(), List.of(pathspec(directory.get()))));
        }
        return Next.of(model.withError(Optional.empty()), new Unstage(added, others));
    }

    /**
     * The repository root has the empty path; git calls it {@code .}.
     */
    private static String pathspec(String directory) {
        return directory.isEmpty() ? "." : directory;
    }
}
