package net.vanfleteren.jazygit.app.cli;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.nio.file.Path;
import net.vanfleteren.jazygit.kernel.settings.ThemeName;

/**
 * How jazygit was asked to start.
 *
 * @param path    where inside the repository to open; the current directory when none
 * @param theme   the theme for this session only, instead of the saved one
 * @param debug   whether to write the debug log
 * @param without the ids of the features to run without, as if they were deleted, for testing and debugging: they
 *                are not even found, as GitHub when gh is on the path (ADR 171)
 */
public record StartupOptions(Option<Path> path, Option<ThemeName> theme, boolean debug, List<String> without) {

    /**
     * Starting as {@code path}, {@code theme} and {@code debug} say, with every feature.
     */
    public StartupOptions(Option<Path> path, Option<ThemeName> theme, boolean debug) {
        this(path, theme, debug, List.empty());
    }
}
