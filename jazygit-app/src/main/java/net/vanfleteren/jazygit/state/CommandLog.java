package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * Appends executed commands to the command log of the model.
 */
public final class CommandLog {

    static final int MAX_ENTRIES = 100;

    private CommandLog() {}

    /**
     * The model with an entry for {@code commands} of {@code operation} added; nothing is added when no
     * command was executed.
     */
    public static Model append(Model model, Operation operation, List<ExecutedCommand> commands) {
        return commands.isEmpty()
                ? model
                : model.mapOutcome(outcome -> outcome.withCommandLog(outcome.commandLog()
                        .append(new LogEntry(operation, commands))
                        .takeRight(MAX_ENTRIES)));
    }
}
