package net.vanfleteren.jazygit.state;

import java.util.List;

/**
 * One operation in the command log.
 *
 * @param title    what was done, e.g. "Push"
 * @param commands the command lines that were executed for it
 */
public record LogEntry(String title, List<String> commands) {

    public LogEntry {
        commands = List.copyOf(commands);
    }
}
