package net.vanfleteren.jazygit.git;

import static net.vanfleteren.jazygit.git.GitCommand.git;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.GitError.CommandFailed;
import net.vanfleteren.jazygit.process.Exec;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitProcessTest {

    @TempDir
    Path dir;

    @Test
    void aSuccessfulCommandReturnsItsOutputAndLine() {
        GitProcess.Output output =
                GitProcess.run(dir, git("init", "-q", "-b", "main")).get();

        assertEquals("git init -q -b main", output.command().line());
        assertEquals(List.of("git init -q -b main"), output.log().map(ExecutedCommand::line));
    }

    @Test
    void aRefusedCommandFailsWithGitsExplanationAndItsLine() {
        GitFailure<GitError.ProcessError> failure =
                GitProcess.run(dir, git("rev-parse", "HEAD")).getLeft();

        CommandFailed failed = (CommandFailed) failure.error();
        assertTrue(failed.says("fatal: not a git repository"), failed.toString());
        assertEquals(128, failed.exitCode());
        assertEquals(List.of("git rev-parse HEAD"), failure.commands().map(ExecutedCommand::line));
    }

    @Test
    void theExplanationPrefersTheErrorOutput() {
        assertEquals(Option.of("error: boom"), Exec.explanation("\n error: boom\nmore", "On branch main"));
        assertEquals(Option.of("On branch main"), Exec.explanation("", "\nOn branch main\n"));
        assertEquals(Option.none(), Exec.explanation("", ""));
    }

    @Test
    void theExplanationIsTheLineThatSaysWhatWentWrong() {
        String rejected = """
                To github.com:o/r.git
                 ! [rejected]        main -> main (fetch first)
                error: failed to push some refs to 'github.com:o/r.git'
                hint: Updates were rejected because the remote contains work that you do not have
                """;

        assertEquals(
                Option.of("error: failed to push some refs to 'github.com:o/r.git'"), Exec.explanation(rejected, ""));
    }

    @Test
    void aRefusalSaysWhatGitPrintedAfterItsExplanationToo() {
        CommandFailed failed = new CommandFailed(
                Option.of("error: patch failed: f:1"),
                1,
                "error: patch failed: f:1\nerror: conflicts in index. Try without --index.\n");

        assertTrue(failed.says("Try without --index"));
    }

    @Test
    void aConfigValueIsNoSubcommand() {
        assertEquals(
                "rebase",
                git("rebase", "--root").withConfig("core.abbrev", "no").operation());
    }

    @Test
    void theOperationIsTheSubcommandAndGivenVariablesOverrideTheDefaults() {
        GitCommand command = git("--no-optional-locks", "status").withEnvironment(HashMap.of("LC_ALL", "nl_BE"));

        assertEquals("status", command.operation());
        assertEquals("nl_BE", command.environment().get("LC_ALL").get());
        assertEquals("0", command.environment().get("GIT_TERMINAL_PROMPT").get());
        assertEquals("false", command.environment().get("GIT_EDITOR").get());
    }
}
