package net.vanfleteren.jazygit.git;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zeroturnaround.exec.ProcessExecutor;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The repository's own git configuration.
 */
class CliConfigTest {

    private static final String KEY = "jazygit.githubUser";

    @TempDir
    Path repo;

    @BeforeEach
    void init() throws Exception {
        new ProcessExecutor().command("git", "init", "-q", repo.toString()).exitValueNormal().execute();
    }

    @Test
    void aKeyThatIsNotSetIsNone() {
        assertEquals(Option.none(), CliConfig.get(repo, KEY).get());
    }

    @Test
    void aKeySetIsReadBack() {
        assertEquals(io.vavr.collection.List.of("git config --local jazygit.githubUser alice-work"),
                CliConfig.set(repo, KEY, Option.of("alice-work")).get().map(ExecutedCommand::line));

        assertEquals(Option.of("alice-work"), CliConfig.get(repo, KEY).get());
    }

    @Test
    void settingNoneRemovesTheKey() {
        CliConfig.set(repo, KEY, Option.of("alice-work")).get();

        CliConfig.set(repo, KEY, Option.none()).get();

        assertEquals(Option.none(), CliConfig.get(repo, KEY).get());
    }

    @Test
    void removingAKeyThatIsNotSetDoesNothing() {
        assertEquals(io.vavr.collection.List.of("git config --local --unset-all jazygit.githubUser"),
                CliConfig.set(repo, KEY, Option.none()).get().map(ExecutedCommand::line));
    }
}
