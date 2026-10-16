package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * One operation in the command log.
 *
 * @param operation what was done, e.g. a push
 * @param commands  the command lines that were executed for it
 */
public record LogEntry(Operation operation, List<ExecutedCommand> commands) {}
