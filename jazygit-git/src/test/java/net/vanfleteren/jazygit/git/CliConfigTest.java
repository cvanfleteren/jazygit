package net.vanfleteren.jazygit.git;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.control.Option;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.model.ConfigValue;
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

    @Test
    void aValueOfTheRepositoryIsListedAsItsOwn() {
        config().setLocal(KEY, Option.of("alice-work")).get();

        assertEquals(
                io.vavr.collection.List.of(new ConfigValue(true, ".git/config", "alice-work")),
                config().all(KEY).get());
    }

    @Test
    void aKeySetNowhereListsNothing() {
        assertEquals(io.vavr.collection.List.empty(), config().all(KEY).get());
    }

    @Test
    void valuesAreListedWithWhereTheyAre() {
        assertEquals(
                io.vavr.collection.List.of(
                        new ConfigValue(false, "/home/alice/.gitconfig", "dark"),
                        new ConfigValue(false, "/home/alice/.gitconfig-work", "light"),
                        new ConfigValue(true, ".git/config", "ember"),
                        new ConfigValue(true, ".git/config.worktree", "mono"),
                        new ConfigValue(false, "command line:", "none")),
                CliConfig.parseValues("""
                        global\tfile:/home/alice/.gitconfig\tdark
                        global\tfile:/home/alice/.gitconfig-work\tlight
                        local\tfile:.git/config\tember
                        worktree\tfile:.git/config.worktree\tmono
                        command\tcommand line:\tnone
                        """));
    }

    @Test
    void aLineThatIsNotAValueIsLeftOut() {
        assertEquals(io.vavr.collection.List.empty(), CliConfig.parseValues("global\tfile:/home/alice/.gitconfig\n"));
    }

    // Through the provider, as the app reaches it.
    private Config config() {
        return CliGit.open(repo).get().config();
    }
}
