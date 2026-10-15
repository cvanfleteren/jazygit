package net.vanfleteren.jazygit.cli;

import java.nio.file.Path;
import java.util.Optional;
import net.vanfleteren.jazygit.settings.ThemeName;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * The arguments jazygit starts with, as picocli reads them: it answers these methods from the parsed command
 * line, so nothing here is ever set.
 */
@Command(name = "jazygit", mixinStandardHelpOptions = true, versionProvider = ManifestVersion.class,
        resourceBundle = StartupArgs.BUNDLE, sortOptions = false)
interface StartupArgs {

    /** The help and error texts of the command line. */
    String BUNDLE = "net.vanfleteren.jazygit.cli";

    @Parameters(arity = "0..1", paramLabel = "PATH", descriptionKey = "path")
    Optional<Path> path();

    @Option(names = "--theme", paramLabel = "THEME", converter = ThemeConverter.class,
            completionCandidates = ThemeIds.class, descriptionKey = "theme")
    Optional<ThemeName> theme();
}
