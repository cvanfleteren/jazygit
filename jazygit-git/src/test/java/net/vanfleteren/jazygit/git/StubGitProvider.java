package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * A {@link GitInfoProvider} for tests: an empty repository whose operations all succeed without running
 * anything. Fakes override what they need.
 */
public interface StubGitProvider extends GitInfoProvider {

    /**
     * Commands as they would have been executed, each written as its arguments separated by spaces.
     */
    static List<ExecutedCommand> executed(String... lines) {
        return List.of(lines).map(line -> new ExecutedCommand(List.of(line.split(" "))));
    }

    /**
     * A successful operation that ran {@code commands}, or nothing.
     */
    static <E extends GitError> Either<GitFailure<E>, List<ExecutedCommand>> ran(String... commands) {
        return Either.right(executed(commands));
    }

    /**
     * An operation that git refused with {@code explanation}, after running {@code command}.
     */
    static Either<GitFailure<ProcessError>, List<ExecutedCommand>> refused(String command, String explanation) {
        return Either.left(new GitFailure<>(new GitError.CommandFailed(explanation, 1), executed(command)));
    }

    @Override
    default String repositoryName() {
        return "repo";
    }

    @Override
    default Path worktree() {
        return Path.of("/repos/repo");
    }

    @Override
    default Branches branches() {
        return new StubBranches() {};
    }

    @Override
    default Stashes stashes() {
        return new StubStashes() {};
    }

    /**
     * Only the main worktree, at {@link #worktree()}.
     */
    @Override
    default Worktrees worktrees() {
        Path shown = worktree();
        return new StubWorktrees() {
            @Override
            public Path shown() {
                return shown;
            }
        };
    }

    @Override
    default History history() {
        return new StubHistory() {};
    }

    @Override
    default Diffs diffs() {
        return new StubDiffs() {};
    }

    @Override
    default Integrate integrate() {
        return new StubIntegrate() {};
    }

    @Override
    default Remotes remotes() {
        return new StubRemotes() {};
    }

    @Override
    default Commits commits() {
        return new StubCommits() {};
    }

    @Override
    default Config config() {
        return new StubConfig() {};
    }

    @Override
    default Index index() {
        return new StubIndex() {};
    }

    @Override
    default Editor editor() {
        return new StubEditor() {};
    }

    @Override
    default Status status() {
        return new StubStatus() {};
    }

    @Override
    default Checkout checkout() {
        return new StubCheckout() {};
    }
}
