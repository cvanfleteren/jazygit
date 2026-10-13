package net.vanfleteren.jazygit.gh;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * A {@code gh} command that failed, and the commands that ran up to and including it.
 */
public record GhFailure(GhError error, List<ExecutedCommand> commands) {
}
