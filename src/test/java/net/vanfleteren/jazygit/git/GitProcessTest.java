package net.vanfleteren.jazygit.git;

import net.vanfleteren.jazygit.process.ExecutedCommand;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.GitError.CommandFailed;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static net.vanfleteren.jazygit.git.GitCommand.git;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitProcessTest {

    @TempDir
    Path dir;

    @Test
    void aSuccessfulCommandReturnsItsOutputAndLine() {
        GitProcess.Output output = GitProcess.run(dir, git("init", "-q", "-b", "main")).get();

        assertEquals("git init -q -b main", output.command().line());
        assertEquals(List.of("git init -q -b main"), output.log().map(ExecutedCommand::line));
    }

    @Test
    void aRefusedCommandFailsWithGitsExplanationAndItsLine() {
        GitFailure<GitError.ProcessError> failure = GitProcess.run(dir, git("rev-parse", "HEAD")).getLeft();

        CommandFailed failed = (CommandFailed) failure.error();
        assertTrue(failed.says("fatal: not a git repository"), failed.toString());
        assertEquals(128, failed.exitCode());
        assertEquals(List.of("git rev-parse HEAD"), failure.commands().map(ExecutedCommand::line));
    }

    @Test
    void theExplanationPrefersTheErrorOutput() {
        assertEquals(Option.of("error: boom"), GitProcess.explanation("\n error: boom\nmore", "On branch main"));
        assertEquals(Option.of("On branch main"), GitProcess.explanation("", "\nOn branch main\n"));
        assertEquals(Option.none(), GitProcess.explanation("", ""));
    }

    @Test
    void theOperationIsTheSubcommandAndGivenVariablesOverrideTheDefaults() {
        GitCommand command = git("--no-optional-locks", "status").withEnvironment(HashMap.of("LC_ALL", "nl_BE"));

        assertEquals("status", command.operation());
        assertEquals("nl_BE", command.environment().get("LC_ALL").get());
        assertEquals("0", command.environment().get("GIT_TERMINAL_PROMPT").get());
    }
}
