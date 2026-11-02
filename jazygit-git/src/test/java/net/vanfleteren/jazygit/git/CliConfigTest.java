package net.vanfleteren.jazygit.git;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.control.Option;
import java.nio.file.Path;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zeroturnaround.exec.ProcessExecutor;

/**
 * The repository's own git configuration.
 */
class CliConfigTest {

    private static final String KEY = "jazygit.githubUser";

    @TempDir
    Path repo;

    @BeforeEach
    void init() throws Exception {
        new ProcessExecutor()
                .command("git", "init", "-q", repo.toString())
                .exitValueNormal()
                .execute();
    }

    @Test
    void aKeyThatIsNotSetIsNone() {
        assertEquals(Option.none(), config().local(KEY).get());
    }

    @Test
    void aKeySetIsReadBack() {
        assertEquals(
                io.vavr.collection.List.of("git config --local jazygit.githubUser alice-work"),
                config().setLocal(KEY, Option.of("alice-work")).get().map(ExecutedCommand::line));

        assertEquals(Option.of("alice-work"), config().local(KEY).get());
    }

    @Test
    void settingNoneRemovesTheKey() {
        config().setLocal(KEY, Option.of("alice-work")).get();

        config().setLocal(KEY, Option.none()).get();

        assertEquals(Option.none(), config().local(KEY).get());
    }

    @Test
    void removingAKeyThatIsNotSetDoesNothing() {
        assertEquals(
                io.vavr.collection.List.of("git config --local --unset-all jazygit.githubUser"),
                config().setLocal(KEY, Option.none()).get().map(ExecutedCommand::line));
    }

    // Through the provider, as the app reaches it.
    private Config config() {
        return CliGitInfoProvider.open(repo).get().config();
    }
}
