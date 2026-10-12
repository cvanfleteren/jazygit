package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.git.model.Sha;

import java.nio.file.Path;

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
    static String changes(Path workTree, Sha sha) {
        return CliDiff.truncated(CliDiff.run(workTree,
                List.of("show", "--format=", "--stat=1000", "--patch", "--no-color", "--no-ext-diff"),
                List.of(sha.value())));
    }
}
