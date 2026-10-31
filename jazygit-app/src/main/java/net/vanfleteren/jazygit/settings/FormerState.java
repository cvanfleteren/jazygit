package net.vanfleteren.jazygit.settings;

import io.vavr.control.Try;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * What jazygit kept of each repository in a file for all of them, before it kept it in a file per repository (ADR 63).
 * It is not carried over: how long the checks took is topped up from GitHub again, and the pull requests that were
 * unseen are taken as seen.
 */
public final class FormerState {

    private FormerState() {}

    /**
     * Deletes how long the checks took next to the state file {@code stateFile}, and the unseen pull requests in it,
     * when they are still there.
     */
    public static Try<Void> forget(Path stateFile) {
        Path timings = SettingsPaths.formerCheckTimingsFile(stateFile);
        return Try.run(() -> {
                    Files.deleteIfExists(timings);
                    Files.deleteIfExists(timings.resolveSibling(timings.getFileName() + ".lock"));
                })
                .flatMap(deleted -> unseen(stateFile)
                        ? TomlFile.change(stateFile, config -> config.<Object>remove(UnseenPullRequestsFile.FORMER_KEY))
                                .<Void>map(removed -> null)
                        : Try.<Void>success(null));
    }

    // Read first, so the state file is not written at every start.
    private static boolean unseen(Path stateFile) {
        return Files.exists(stateFile)
                && TomlFile.read(stateFile)
                        .toOption()
                        .exists(config -> config.contains(UnseenPullRequestsFile.FORMER_KEY));
    }
}
