package net.vanfleteren.jazygit.git;

import java.nio.file.Path;
import java.util.List;

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
    static String changes(Path workTree, String sha) {
        return CliDiff.truncated(CliDiff.run(workTree,
                List.of("show", "--format=", "--stat=1000", "--patch", "--no-color", "--no-ext-diff"),
                List.of(sha)));
    }
}
