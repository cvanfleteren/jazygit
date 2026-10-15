package net.vanfleteren.jazygit.cli;

import io.vavr.control.Option;
import java.nio.file.Path;
import net.vanfleteren.jazygit.settings.ThemeName;

/**
 * How jazygit was asked to start.
 *
 * @param path  where inside the repository to open; the current directory when none
 * @param theme the theme for this session only, instead of the saved one
 */
public record StartupOptions(Option<Path> path, Option<ThemeName> theme) {}
