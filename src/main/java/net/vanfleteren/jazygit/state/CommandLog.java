package net.vanfleteren.jazygit.state;

import net.vanfleteren.jazygit.i18n.Messages;

import java.util.ArrayList;
import java.util.List;

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
        List<LogEntry> log = new ArrayList<>(model.commandLog());
        log.add(new LogEntry(Messages.get(titleKey), commands));
        return model.withCommandLog(log.subList(Math.max(0, log.size() - MAX_ENTRIES), log.size()));
    }
}
