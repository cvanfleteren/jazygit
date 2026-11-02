package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.model.CommitFile;
import net.vanfleteren.jazygit.git.model.DiffOptions;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.Stash;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * Listing and changing stashes, as git stash does (ADR 69).
 */
public interface Stashes {

    /**
     * The stashes, newest first.
     */
    Either<ProcessError, List<Stash>> list();

    /**
     * What the stash {@code sha} stashed, like {@code git stash show}: the changed files, untracked ones too, a
     * summary of the changes, a blank line and the diff, read with {@code options}. Long output is cut off.
     */
    Either<ProcessError, DiffText> changes(Sha sha, DiffOptions options);

    /**
     * The files the stash {@code sha} changed, untracked ones too.
     */
    Either<ProcessError, List<CommitFile>> files(Sha sha);

    /**
     * What the stash {@code sha} stashed of {@code files}: only the diff, untracked files too, read with
     * {@code options}. Long output is cut off.
     */
    Either<ProcessError, DiffText> fileChanges(Sha sha, List<CommitFile> files, DiffOptions options);

    /**
     * Stashes the changes, staged and unstaged, and the untracked files, under the name {@code message}.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> push(Option<String> message);

    /**
     * Applies {@code stash} to the working tree and drops it. When that conflicts, the stash is kept.
     */
    Either<GitFailure<GitError>, List<ExecutedCommand>> pop(Stash stash);

    /**
     * Deletes {@code stash} without applying it.
     */
    Either<GitFailure<GitError>, List<ExecutedCommand>> drop(Stash stash);
}
