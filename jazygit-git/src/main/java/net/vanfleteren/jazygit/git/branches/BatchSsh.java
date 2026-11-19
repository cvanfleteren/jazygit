package net.vanfleteren.jazygit.git.branches;

import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.util.function.Function;
import net.vanfleteren.jazygit.process.SshAsking;

/**
 * ssh run in batch mode, so that it fails instead of asking anything: what git runs as ssh, {@code GIT_SSH_COMMAND} or
 * {@code core.sshCommand}, with {@code -o BatchMode=yes}.
 */
public enum BatchSsh implements SshAsking {
    INSTANCE;

    @Override
    public <T> Asked<T> asking(Path directory, Function<Map<String, String>, T> talk) {
        return Asked.of(talk.apply(environment(configured(directory))));
    }

    /**
     * The ssh command git runs in {@code worktree}: {@code GIT_SSH_COMMAND}, else {@code core.sshCommand}; none when it
     * runs plain {@code ssh}, or the configuration cannot be read.
     */
    public static Option<String> configured(Path worktree) {
        return CliRemotes.configuredSsh(worktree, Option.of(System.getenv("GIT_SSH_COMMAND")))
                .getOrElse(Option.none());
    }

    /**
     * The variables that run {@code ssh}, the configured ssh command, in batch mode.
     */
    public static Map<String, String> environment(Option<String> ssh) {
        return HashMap.of("GIT_SSH_COMMAND", CliRemotes.batchMode(ssh));
    }
}
