package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.branches.StubBranches;
import net.vanfleteren.jazygit.git.branches.StubCheckout;
import net.vanfleteren.jazygit.git.branches.StubConfig;
import net.vanfleteren.jazygit.git.branches.StubRemotes;
import net.vanfleteren.jazygit.git.branches.StubWorktrees;
import net.vanfleteren.jazygit.git.commits.StubCommits;
import net.vanfleteren.jazygit.git.files.StubDiffs;
import net.vanfleteren.jazygit.git.files.StubEditor;
import net.vanfleteren.jazygit.git.files.StubIndex;
import net.vanfleteren.jazygit.git.files.StubStashes;
import net.vanfleteren.jazygit.git.files.StubStatus;
import net.vanfleteren.jazygit.git.history.StubHistory;
import net.vanfleteren.jazygit.git.history.StubIntegrate;
import net.vanfleteren.jazygit.git.process.GitError;
import net.vanfleteren.jazygit.git.process.GitFailure;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * A {@link Git} for tests: an empty repository whose operations all succeed without running anything, each role a
 * stub of its own. A test replaces the roles it needs: {@code StubGit.of().withBranches(new StubBranches() { … })}
 * (ADR 69).
 */
public final class StubGit {

    /**
     * Where the stub repository is, unless a test says otherwise.
     */
    public static final Path WORKTREE = Path.of("/repos/repo");

    private StubGit() {}

    /**
     * The stub repository {@code repo} at {@link #WORKTREE}.
     */
    public static Git of() {
        return of(WORKTREE);
    }

    /**
     * A stub repository at {@code worktree}, named {@code repo}.
     */
    public static Git of(Path worktree) {
        return of(worktree, "repo");
    }

    /**
     * A stub repository at {@code worktree}, named {@code name}, whose only worktree is the main one, there.
     */
    public static Git of(Path worktree, String name) {
        return new Git(
                worktree,
                name,
                new StubStatus() {},
                new StubBranches() {},
                new StubCheckout() {},
                new StubHistory() {},
                new StubDiffs() {},
                new StubStashes() {},
                new StubCommits() {},
                new StubIntegrate() {},
                new StubRemotes() {},
                new StubIndex() {},
                new StubWorktrees() {
                    @Override
                    public Path shown() {
                        return worktree;
                    }
                },
                new StubConfig() {},
                new StubEditor() {});
    }

    /**
     * Commands as they would have been executed, each written as its arguments separated by spaces.
     */
    public static List<ExecutedCommand> executed(String... lines) {
        return List.of(lines).map(line -> new ExecutedCommand(List.of(line.split(" "))));
    }

    /**
     * A successful operation that ran {@code commands}, or nothing.
     */
    public static Either<GitFailure, List<ExecutedCommand>> ran(String... commands) {
        return Either.right(executed(commands));
    }

    /**
     * An operation that git refused with {@code explanation}, after running {@code command}.
     */
    public static Either<GitFailure, List<ExecutedCommand>> refused(String command, String explanation) {
        return Either.left(new GitFailure(new GitError.CommandFailed(explanation, 1), executed(command)));
    }
}
