package net.vanfleteren.jazygit.git;

import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.Sha;

import java.nio.file.Path;

import static net.vanfleteren.jazygit.git.GitCommand.git;

/**
 * Reads what a commit changed through the git command line tool.
 */
final class CliShow {

    private CliShow() {
    }

    /**
     * The changed files with their change counts, a summary line, a blank line and the diff, in one
     * git call however many files the commit touches. Long output is cut off.
     */
    static Either<ProcessError, DiffText> changes(Path workTree, Sha sha) {
        return GitProcess.read(workTree,
                GitCommand.reading("show", "--format=", "--stat=1000", "--patch", "--no-color", "--no-ext-diff",
                        "--no-show-signature", sha.value()),
                output -> Either.right(CliDiff.truncated(output)));
    }
}
