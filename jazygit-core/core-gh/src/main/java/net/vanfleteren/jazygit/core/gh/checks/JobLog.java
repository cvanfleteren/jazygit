package net.vanfleteren.jazygit.core.gh.checks;

import io.vavr.collection.List;
import java.nio.file.Path;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;

/**
 * The log of a job of GitHub Actions, kept in a file (ADR 160).
 *
 * @param file     the file it is kept in
 * @param line     the line to open it at, counted from 1: the first that says {@code ##[error]}, else the last
 * @param commands the commands that read it from GitHub; none when it was kept already
 */
public record JobLog(Path file, int line, List<ExecutedCommand> commands) {}
