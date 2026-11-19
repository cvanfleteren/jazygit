package net.vanfleteren.jazygit.kernel.state.operation;

import io.vavr.collection.List;
import java.time.Instant;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;

/**
 * One operation in the command log.
 *
 * @param operation what was done, e.g. a push
 * @param commands  the command lines that were executed for it
 * @param at        when it was done: when the model learned how it went
 */
public record LogEntry(Operation operation, List<ExecutedCommand> commands, Instant at) {}
