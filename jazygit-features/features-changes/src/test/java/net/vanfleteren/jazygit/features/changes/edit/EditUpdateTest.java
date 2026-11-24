package net.vanfleteren.jazygit.features.changes.edit;

import static net.vanfleteren.jazygit.kernel.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.util.ArrayList;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.git.StubGit;
import net.vanfleteren.jazygit.core.git.files.StubEditor;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.PullRequestCommits;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.settings.RecentRepositories;
import net.vanfleteren.jazygit.kernel.settings.SettingsStore;
import net.vanfleteren.jazygit.kernel.state.AppError;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Repositories;
import net.vanfleteren.jazygit.kernel.state.Terminal;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.diff.BaseTip;
import net.vanfleteren.jazygit.kernel.state.diff.Changes;
import net.vanfleteren.jazygit.kernel.state.operation.OperationMsg;
import net.vanfleteren.jazygit.kernel.state.view.CommitFilesView;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;
import org.junit.jupiter.api.Test;

/**
 * Opening files in the editor.
 */
class EditUpdateTest {

    private static final ExecutedCommand NVIM = ExecutedCommand.of("nvim", "src/A.java");
    private static final PullRequestCommits PULL_REQUEST =
            new PullRequestCommits(7, "owner/repo", "", new Sha("b".repeat(40)), new Sha("a".repeat(40)));

    @Test
    void aFileIsOpenedInTheEditor() {
        Next next = Update.update(loaded(), new EditMsg.Requested("src/A.java"));

        assertEquals(List.of(new EditCmd(List.of("src/A.java"))), next.cmds());
    }

    @Test
    void theEditorRunsWithTheTerminalHandedOver() {
        java.util.List<String> events = new ArrayList<>();
        Terminal terminal = new Terminal() {
            @Override
            public <T> T handOver(Supplier<T> program) {
                events.add("leave");
                T result = program.get();
                events.add("come back");
                return result;
            }
        };
        Git git = StubGit.of().withEditor(new StubEditor() {
            @Override
            public Either<GitFailure, List<ExecutedCommand>> edit(List<String> files) {
                events.add("edit " + files.mkString(" "));
                return Either.right(List.of(NVIM));
            }
        });

        Msg done = new EditCmd(List.of("src/A.java")).run(env(git, terminal));

        assertEquals(java.util.List.of("leave", "edit src/A.java", "come back"), events);
        assertEquals(new OperationMsg.Succeeded(new EditCmd(List.of("src/A.java")), List.of(NVIM)), done);
    }

    @Test
    void anEditorThatFailsIsReported() {
        Git git = StubGit.of().withEditor(new StubEditor() {
            @Override
            public Either<GitFailure, List<ExecutedCommand>> edit(List<String> files) {
                return Either.left(new GitFailure(new GitError.EditorFailed("nvim", 1), List.of(NVIM)));
            }
        });
        EditCmd cmd = new EditCmd(List.of("src/A.java"));

        Next next = Update.update(loaded(), cmd.run(env(git, Terminal.NONE)));

        AppError.OperationFailed failed = assertInstanceOf(
                AppError.OperationFailed.class, next.model().outcome().error().get());
        assertEquals(EditCmd.EDIT, failed.operation());
        assertTrue(next.model().outcome().commandLog().nonEmpty());
    }

    @Test
    void aFileOfThePullRequestOfTheBranchCheckedOutIsOpened() {
        Next next = Update.update(
                listing(new Changes.OfPullRequest(PULL_REQUEST, Option.of(new BranchName("main")))),
                new EditMsg.ListedRequested("src/A.java"));

        assertEquals(List.of(new EditCmd(List.of("src/A.java"))), next.cmds());
    }

    @Test
    void aFileOfAPullRequestWhoseBranchIsNotCheckedOutIsNotOpened() {
        Next next = Update.update(
                listing(new Changes.OfPullRequest(PULL_REQUEST, Option.of(new BranchName("feature")))),
                new EditMsg.ListedRequested("src/A.java"));

        assertEquals(List.empty(), next.cmds());
        assertEquals(
                Option.of(new EditError.NotCheckedOut("feature")),
                next.model().outcome().error());
    }

    @Test
    void aFileOfAPullRequestOfNoBranchKnownIsNotOpened() {
        Next next = Update.update(
                listing(new Changes.OfPullRequest(PULL_REQUEST)), new EditMsg.ListedRequested("src/A.java"));

        assertEquals(List.empty(), next.cmds());
        assertEquals(
                Option.of(new EditError.NotCheckedOut("#7")),
                next.model().outcome().error());
    }

    @Test
    void aFileOfABranchIsOpenedOnlyWhileItIsCheckedOut() {
        BaseTip base = new BaseTip(new BranchRef.Local(new BranchName("main")), new Sha("aaaa"));

        Next checkedOut = Update.update(
                listing(new Changes.OfTip(new BranchName("main"), new Sha("aaaa"))),
                new EditMsg.ListedRequested("src/A.java"));
        Next elsewhere = Update.update(
                listing(new Changes.OfBranch(new BranchName("feature"), base, new Sha("ffff"))),
                new EditMsg.ListedRequested("src/A.java"));

        assertEquals(List.of(new EditCmd(List.of("src/A.java"))), checkedOut.cmds());
        assertEquals(List.empty(), elsewhere.cmds());
        assertEquals(
                Option.of(new EditError.NotCheckedOut("feature")),
                elsewhere.model().outcome().error());
    }

    @Test
    void aFileOfACommitOrAStashOpensTheFileOfTheWorkingTree() {
        Next commit = Update.update(
                listing(new Changes.OfCommit(new Sha("aaaa"))), new EditMsg.ListedRequested("src/A.java"));
        Next stash =
                Update.update(listing(new Changes.OfStash(new Sha("cccc"))), new EditMsg.ListedRequested("src/A.java"));

        assertEquals(List.of(new EditCmd(List.of("src/A.java"))), commit.cmds());
        assertEquals(List.of(new EditCmd(List.of("src/A.java"))), stash.cmds());
    }

    @Test
    void aFileListedWithoutFilesOfAChangeListedOpensNothing() {
        Next next = Update.update(loaded(), new EditMsg.ListedRequested("src/A.java"));

        assertEquals(List.empty(), next.cmds());
    }

    /**
     * The sample model, with {@code main} checked out, listing the files of {@code changes}.
     */
    private static Model listing(Changes changes) {
        return loaded().mapRepoView(repoView -> repoView.mapFiles(files -> files.withCommitFiles(
                Option.of(CommitFilesView.of(changes, Scopes.BRANCHES.pane(), Scopes.FILES.pane())))));
    }

    private static Env env(Git git, Terminal terminal) {
        return new Env(git, SettingsStore.NONE, RecentRepositories.NONE, Repositories.NONE, terminal);
    }
}
