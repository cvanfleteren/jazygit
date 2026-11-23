package net.vanfleteren.jazygit.app;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.vavr.collection.List;
import io.vavr.control.Either;
import java.nio.file.Path;
import net.vanfleteren.jazygit.core.git.StubGit;
import net.vanfleteren.jazygit.core.git.branches.BatchSsh;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.kernel.settings.Settings;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Repositories;
import net.vanfleteren.jazygit.kernel.state.Terminal;
import net.vanfleteren.jazygit.kernel.state.launch.ProgramFailure;
import net.vanfleteren.jazygit.kernel.state.launch.ProgramLauncher;
import org.junit.jupiter.api.Test;

/**
 * The programs the user set are started by the app's launcher in every repository it opens (ADR 183).
 */
class RepositoryEnvTest {

    // One that is not NONE, to tell apart.
    private static final ProgramLauncher LAUNCHER = new ProgramLauncher() {
        @Override
        public Either<ProgramFailure, ExecutedCommand> run(Path directory, List<String> commandLine) {
            return Either.right(new ExecutedCommand(commandLine));
        }

        @Override
        public Either<ProgramFailure, ExecutedCommand> start(Path directory, List<String> commandLine) {
            return Either.right(new ExecutedCommand(commandLine));
        }
    };

    @Test
    void theAppStartsProgramsItself() {
        Env env = new Env(StubGit.of()).withApp(Repositories.NONE, Terminal.NONE);

        assertNotSame(ProgramLauncher.NONE, env.launcher());
    }

    @Test
    void anotherRepositoryOpenedStartsProgramsAsTheFirstDid() {
        Env env = new Env(StubGit.of()).with(LAUNCHER);

        Env reopened = RepositoryEnv.reopen(env, StubGit.of(), Settings.DEFAULT, BatchSsh.INSTANCE);

        assertSame(LAUNCHER, reopened.launcher());
    }
}
