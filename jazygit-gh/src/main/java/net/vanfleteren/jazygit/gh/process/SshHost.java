package net.vanfleteren.jazygit.gh.process;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.time.Duration;
import net.vanfleteren.jazygit.gh.model.GhAccount;
import net.vanfleteren.jazygit.process.Exec;

/**
 * The host an ssh alias of a remote stands for, as gh resolves it too: for the token of a configured account, and for
 * the browser of the account a repository uses (ADR 88).
 */
public final class SshHost {

    private static final Duration SSH_TIMEOUT = Duration.ofSeconds(5);

    private SshHost() {}

    /**
     * The host that {@code host} stands for: itself, or the host name the ssh configuration gives an alias such as
     * {@code github-work}. Asking ssh, in {@code worktree}, is left out for GitHub itself.
     */
    public static String resolve(Path worktree, String host) {
        return GhAccount.isGitHubCom(host)
                ? host
                : Exec.run(worktree, List.of("ssh", "-G", host), HashMap.empty(), SSH_TIMEOUT)
                        .toOption()
                        .flatMap(SshHost::hostName)
                        .getOrElse(host);
    }

    /**
     * The {@code hostname} that {@code ssh -G} prints.
     */
    static Option<String> hostName(String raw) {
        return List.of(raw.split("\\R"))
                .map(String::strip)
                .find(line -> line.startsWith("hostname "))
                .map(line -> line.substring("hostname ".length()).strip())
                .filter(name -> !name.isEmpty());
    }
}
