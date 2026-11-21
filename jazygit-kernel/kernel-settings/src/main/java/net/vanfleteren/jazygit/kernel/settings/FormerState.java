package net.vanfleteren.jazygit.kernel.settings;

import io.vavr.control.Try;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * What jazygit kept of each repository in a file for all of them, before it kept it in a file per repository (ADR 63).
 * It is not carried over, only deleted; which keys and files those were is for the feature that kept them to say
 * (ADR 171).
 */
public final class FormerState {

    private FormerState() {}

    /**
     * Deletes the file {@code file}, with its lock, and the key {@code key} in the state file {@code stateFile}, when
     * they are still there.
     */
    public static Try<Void> forget(Path stateFile, String key, Path file) {
        return Try.run(() -> {
                    Files.deleteIfExists(file);
                    Files.deleteIfExists(file.resolveSibling(file.getFileName() + ".lock"));
                })
                .flatMap(deleted -> kept(stateFile, key)
                        ? TomlFile.change(stateFile, config -> config.<Object>remove(key))
                                .<Void>map(removed -> null)
                        : Try.<Void>success(null));
    }

    // Read first, so the state file is not written at every start.
    private static boolean kept(Path stateFile, String key) {
        return Files.exists(stateFile) && TomlFile.read(stateFile).toOption().exists(config -> config.contains(key));
    }
}
