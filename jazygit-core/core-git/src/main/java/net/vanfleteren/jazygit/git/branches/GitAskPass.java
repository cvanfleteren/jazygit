package net.vanfleteren.jazygit.git.branches;

import io.vavr.control.Option;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.process.GitCommand;
import net.vanfleteren.jazygit.git.process.GitProcess;

/**
 * The program git asks the user for credentials through, as the configuration has it: {@code core.askPass}.
 */
public final class GitAskPass {

    private GitAskPass() {}

    /**
     * The {@code core.askPass} of {@code worktree}; none when it is not set, or the configuration cannot be read.
     */
    public static Option<String> configured(Path worktree) {
        // git config --get exits with 1, saying nothing, when the key is not set.
        return GitProcess.ask(worktree, GitCommand.reading("config", "--get", "core.askPass"))
                .map(answer -> answer.map(output -> output.stdout().strip()).filter(program -> !program.isEmpty()))
                .getOrElse(Option.none());
    }
}
