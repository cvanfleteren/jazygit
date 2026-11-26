package net.vanfleteren.jazygit.kernel.state.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.StubGit;
import net.vanfleteren.jazygit.core.git.branches.StubConfig;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Msg;
import org.junit.jupiter.api.Test;

/**
 * Reading what the user chose before as the home of a repository from its git configuration (ADR 177).
 */
class ReadForgeChoiceTest {

    @Test
    void nothingKeptIsNothingChosen() {
        assertEquals(new ForgeMsg.Remembered(new ForgeChoice.Unchosen()), read(HashMap.empty()));
    }

    @Test
    void anEmptyValueIsNoForge() {
        assertEquals(new ForgeMsg.Remembered(new ForgeChoice.NoForge()), read(HashMap.of(ReadForgeChoice.KEY, "")));
    }

    @Test
    void aRemoteIsReadWithoutTheSpaceAroundIt() {
        assertEquals(
                new ForgeMsg.Remembered(new ForgeChoice.Remote(new RemoteName("codeberg"))),
                read(HashMap.of(ReadForgeChoice.KEY, " codeberg ")));
    }

    @Test
    void aConfigurationThatCannotBeReadIsNothingChosen() {
        Env env = new Env(StubGit.of().withConfig(new StubConfig() {
            @Override
            public Either<GitError, Option<String>> local(String key) {
                return Either.left(new GitError.Interrupted("config"));
            }
        }));

        assertEquals(new ForgeMsg.Remembered(new ForgeChoice.Unchosen()), new ReadForgeChoice().run(env));
    }

    @Test
    void aFailedReadIsNothingChosen() {
        assertEquals(
                new ForgeMsg.Remembered(new ForgeChoice.Unchosen()),
                new ReadForgeChoice().failed(new Failure.Bug(new Exception())));
    }

    @Test
    void noForgeKeptIsReadAsNoForge() {
        Kept config = new Kept();
        Env env = new Env(StubGit.of().withConfig(config));

        new SaveForgeChoice(Option.none()).run(env);

        assertEquals(new ForgeMsg.Remembered(new ForgeChoice.NoForge()), new ReadForgeChoice().run(env));
    }

    @Test
    void aRemoteKeptIsReadAsThatRemote() {
        Kept config = new Kept();
        Env env = new Env(StubGit.of().withConfig(config));

        new SaveForgeChoice(Option.of(new RemoteName("origin"))).run(env);

        assertEquals(
                new ForgeMsg.Remembered(new ForgeChoice.Remote(new RemoteName("origin"))),
                new ReadForgeChoice().run(env));
    }

    private static Msg read(Map<String, String> values) {
        Env env = new Env(StubGit.of().withConfig(new StubConfig() {
            @Override
            public Either<GitError, Option<String>> local(String key) {
                return Either.right(values.get(key));
            }
        }));
        return new ReadForgeChoice().run(env);
    }

    // A configuration that keeps what is set, to be read back.
    private static final class Kept implements StubConfig {

        private final java.util.Map<String, String> values = new java.util.HashMap<>();

        @Override
        public Either<GitError, Option<String>> local(String key) {
            return Either.right(Option.of(values.get(key)));
        }

        @Override
        public Either<GitFailure, List<ExecutedCommand>> setLocal(String key, Option<String> value) {
            value.fold(() -> values.remove(key), set -> values.put(key, set));
            return Either.right(List.empty());
        }
    }
}
