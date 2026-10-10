package net.vanfleteren.jazygit.state;

import java.util.List;

/**
 * A message preceded by commands to log, used for commands that failed: the commands that were executed
 * still belong in the command log.
 *
 * @param titleKey the message key of the log entry's title
 * @param commands the command lines that were executed, the failing one last
 * @param msg      the message to apply once they are logged
 */
public record LoggedMsg(String titleKey, List<String> commands, Msg msg) implements Msg {

    public LoggedMsg {
        commands = List.copyOf(commands);
    }

    @Override
    public Update.Next apply(Model model) {
        return msg.apply(CommandLog.append(model, titleKey, commands));
    }
}
