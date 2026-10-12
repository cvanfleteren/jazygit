package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.i18n.Messages;

/**
 * Appends executed commands to the command log of the model.
 */
public final class CommandLog {

    static final int MAX_ENTRIES = 100;

    private CommandLog() {
    }

    /**
     * The model with an entry titled by the message {@code titleKey} added for {@code commands}; nothing
     * is added when no command was executed.
     */
    public static Model append(Model model, String titleKey, List<String> commands) {
        if (commands.isEmpty()) {
            return model;
        }
        return model.withCommandLog(model.commandLog()
                .append(new LogEntry(Messages.get(titleKey), commands))
                .takeRight(MAX_ENTRIES));
    }
}
