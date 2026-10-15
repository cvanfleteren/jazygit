package net.vanfleteren.jazygit.git;

import net.vanfleteren.jazygit.process.ExecutedCommand;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.MergeMode;

/**
 * Merging branches, as {@code git merge} does.
 */
public interface GitMerge {

    /**
     * Merges {@code branch} into the checked out branch, as {@code mode} says. A merge that stops on conflicts
     * is undone again, so the repository is left as it was. Returns the command lines it ran, or why it failed
     * together with the command lines run up to then.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> merge(Branch.Name branch, MergeMode mode);
}
