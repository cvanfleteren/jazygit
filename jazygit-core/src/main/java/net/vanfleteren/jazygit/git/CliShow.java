package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.model.DiffOptions;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.Sha;

/**
 * Reads what a commit changed through the git command line tool.
 */
final class CliShow {

    private CliShow() {}

    /**
     * The changed files with their change counts, a summary line, a blank line and the diff, in one
     * git call however many files the commit touches, read with {@code options}. Long output is cut off.
     */
    static Either<ProcessError, DiffText> changes(Path worktree, Sha sha, DiffOptions options) {
        return GitProcess.read(
                worktree,
                GitCommand.reading(List.of(
                                "show",
                                "--format=",
                                "--stat=1000",
                                "--patch",
                                "--no-color",
                                "--no-ext-diff",
                                "--no-show-signature")
                        .appendAll(CliDiff.flags(options))
                        .append(sha.value())),
                output -> Either.right(CliDiff.truncated(output)));
    }
}
