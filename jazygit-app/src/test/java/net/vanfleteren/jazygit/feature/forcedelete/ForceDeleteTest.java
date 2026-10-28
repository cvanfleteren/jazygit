package net.vanfleteren.jazygit.feature.forcedelete;

import static net.vanfleteren.jazygit.git.StubGitProvider.executed;
import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import net.vanfleteren.jazygit.feature.branch.delete.DeleteBranchCmd.DeleteBranch;
import net.vanfleteren.jazygit.feature.worktree.remove.RemoveWorktreeCmd;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.git.StubGitProvider;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.OperationMsg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

/**
 * Deleting a local branch that git refuses to delete as it is not merged: deleted with force straight away when no
 * commit would be lost, else only once the user agreed to lose them.
 */
class ForceDeleteTest {

    private static final BranchName MAIN = new BranchName("main");
    private static final BranchName FEATURE = new BranchName("feature");
    private static final BranchRef ORIGIN_FEATURE = new BranchRef.Remote(new RemoteBranch("origin", FEATURE));
    private static final DeleteBranch DELETE_BOTH =
            new DeleteBranch(FEATURE, new RemoteBranch("origin", FEATURE), DeleteScope.BOTH);
    private static final GitFailure<GitError> NOT_MERGED = new GitFailure<>(
            new GitError.NotFullyMerged("error: the branch 'feature' is not fully merged"),
            executed("git push origin --delete feature", "git branch --delete -- feature"));

    private final java.util.List<String> ran = new ArrayList<>();

    /**
     * main, checked out, and feature.
     */
    private static Model model() {
        return TestModels.localBranchesRead(
                        loaded(),
                        List.of(
                                Fixtures.branch(MAIN, true, new Sha("aaaa")),
                                Fixtures.branch(FEATURE, false, new Sha("ffff"))))
                .model();
    }

    /**
     * A git that refuses to delete a branch with {@code deleted}, finds {@code lost} commits only on it, and records
     * deleting it with force.
     */
    private GitInfoProvider git(
            Either<GitFailure<GitError>, List<ExecutedCommand>> deleted, Either<ProcessError, Integer> lost) {
        return new StubGitProvider() {
            @Override
            public Either<GitFailure<GitError>, List<ExecutedCommand>> deleteBranch(
                    BranchName name, RemoteBranch remote, DeleteScope scope) {
                return deleted;
            }

            @Override
            public Either<GitFailure<GitError>, List<ExecutedCommand>> deleteLocalBranch(BranchName name) {
                ran.add("delete " + name.value());
                return deleted;
            }

            @Override
            public Either<ProcessError, Integer> countOnlyOn(BranchName name) {
                ran.add("count " + name.value());
                return lost;
            }

            @Override
            public Either<GitFailure<ProcessError>, List<ExecutedCommand>> forceDeleteLocalBranch(BranchName name) {
                ran.add("force " + name.value());
                return StubGitProvider.ran("git branch --delete --force -- " + name.value());
            }
        };
    }

    private static Env env(GitInfoProvider git) {
        return new Env(git, StubGitHub.empty());
    }

    @Test
    void aBranchWithCommitsNoOtherBranchHasIsNotDeletedButAsksToLoseThem() {
        assertEquals(
                new ForceDeleteMsg.NotFullyMerged(DELETE_BOTH, FEATURE, 3, NOT_MERGED.commands()),
                DELETE_BOTH.run(env(git(Either.left(NOT_MERGED), Either.right(3)))));
        assertEquals(java.util.List.of("count feature"), ran);
    }

    @Test
    void aBranchWhoseCommitsAreAllOnAnotherRefIsDeletedWithForceWithoutAsking() {
        assertEquals(
                new OperationMsg.Succeeded(
                        DELETE_BOTH,
                        executed(
                                "git push origin --delete feature",
                                "git branch --delete -- feature",
                                "git branch --delete --force -- feature")),
                DELETE_BOTH.run(env(git(Either.left(NOT_MERGED), Either.right(0)))));
        assertEquals(java.util.List.of("count feature", "force feature"), ran);
    }

    @Test
    void whenTheCommitsCannotBeCountedTheRefusalIsReported() {
        assertEquals(
                DELETE_BOTH.failedWith(NOT_MERGED),
                DELETE_BOTH.run(
                        env(git(Either.left(NOT_MERGED), Either.left(new GitError.CommandFailed("bad", 128))))));
        assertEquals(java.util.List.of("count feature"), ran);
    }

    @Test
    void anyOtherFailureIsReportedWithoutCounting() {
        GitFailure<GitError> locked = new GitFailure<>(new GitError.CommandFailed("ref is locked", 1), List.empty());

        assertEquals(DELETE_BOTH.failedWith(locked), DELETE_BOTH.run(env(git(Either.left(locked), Either.right(3)))));
        assertEquals(java.util.List.of(), ran);
    }

    @Test
    void removingAWorktreeWithItsUnmergedBranchAsksTheSameOnceTheWorktreeIsGone() {
        RemoveWorktreeCmd remove = new RemoveWorktreeCmd("/repos/app-feature", false, Option.of(FEATURE));
        GitInfoProvider git = new StubGitProvider() {
            @Override
            public Either<GitFailure<GitError>, List<ExecutedCommand>> removeWorktree(Path location, boolean force) {
                return StubGitProvider.ran("git worktree remove " + location);
            }

            @Override
            public Either<GitFailure<GitError>, List<ExecutedCommand>> deleteLocalBranch(BranchName name) {
                return Either.left(new GitFailure<>(
                        new GitError.NotFullyMerged("not fully merged"), executed("git branch --delete -- feature")));
            }

            @Override
            public Either<ProcessError, Integer> countOnlyOn(BranchName name) {
                return Either.right(2);
            }
        };

        assertEquals(
                new ForceDeleteMsg.NotFullyMerged(
                        remove,
                        FEATURE,
                        2,
                        executed("git worktree remove /repos/app-feature", "git branch --delete -- feature")),
                remove.run(env(git)));
    }

    @Test
    void theRefusalAsksToDeleteAnywayLogsWhatRanAndReleasesTheBranch() {
        Model deleting = TestModels.running(model(), DELETE_BOTH);

        Model asked = TestModels.done(
                        deleting,
                        DELETE_BOTH,
                        new ForceDeleteMsg.NotFullyMerged(DELETE_BOTH, FEATURE, 3, NOT_MERGED.commands()))
                .model();

        assertEquals(Option.of(new ForceDeleteBranchPopup(FEATURE, 3, false)), asked.popup());
        assertEquals(Option.none(), asked.jobs().busy().get(new BranchRef.Local(FEATURE)));
        assertEquals(Option.none(), TestModels.errorText(asked));
        assertEquals(
                List.of("git push origin --delete feature", "git branch --delete -- feature"),
                asked.outcome().commandLog().flatMap(entry -> entry.commands()).map(ExecutedCommand::line));
    }

    @Test
    void theRefusalOfABranchWhoseUpstreamIsGoneSaysSo() {
        Model asked = Update.update(
                        tracking(new Tracking.Gone(ORIGIN_FEATURE)),
                        new ForceDeleteMsg.NotFullyMerged(DELETE_BOTH, FEATURE, 2, NOT_MERGED.commands()))
                .model();

        assertEquals(Option.of(new ForceDeleteBranchPopup(FEATURE, 2, true)), asked.popup());
    }

    @Test
    void anUpstreamDeletedAlongWithTheBranchDoesNotCountAsGone() {
        Model asked = Update.update(
                        tracking(new Tracking.Tracks(new Upstream(ORIGIN_FEATURE, 2, 0))),
                        new ForceDeleteMsg.NotFullyMerged(DELETE_BOTH, FEATURE, 2, NOT_MERGED.commands()))
                .model();

        assertEquals(Option.of(new ForceDeleteBranchPopup(FEATURE, 2, false)), asked.popup());
    }

    /**
     * main, checked out, and feature, with {@code tracking}.
     */
    private static Model tracking(Tracking tracking) {
        return TestModels.localBranchesRead(
                        loaded(),
                        List.of(
                                Fixtures.branch(MAIN, true, new Sha("aaaa")),
                                Fixtures.branch(
                                        FEATURE, false, new Sha("ffff"), Instant.EPOCH, false, false, tracking)))
                .model();
    }

    @Test
    void confirmingDeletesTheBranchWithForce() {
        Model asked = model().openPopup(new ForceDeleteBranchPopup(FEATURE, 3, false));

        Next confirmed = TestModels.stepped(asked, new ForceDeleteMsg.Confirmed());

        assertEquals(List.of(new ForceDeleteCmd(FEATURE)), confirmed.cmds());
        assertEquals(Option.none(), confirmed.model().popup());
        assertEquals(
                Option.of(ForceDeleteCmd.FORCE_DELETE_BRANCH),
                confirmed.model().jobs().busy().get(new BranchRef.Local(FEATURE)));
    }

    @Test
    void cancellingKeepsTheBranch() {
        Model asked = model().openPopup(new ForceDeleteBranchPopup(FEATURE, 3, false));

        Next cancelled = Update.update(asked, new Msg.PopupDismissed(ForceDeleteBranchPopup.class));

        assertEquals(List.empty(), cancelled.cmds());
        assertEquals(Option.none(), cancelled.model().popup());
    }

    @Test
    void confirmingBeforeTheRefreshAfterRemovingItsWorktreeStillDeletesTheBranch() {
        Model stale = TestModels.localBranchesRead(
                        loaded(),
                        List.of(
                                Fixtures.branch(MAIN, true, new Sha("aaaa")),
                                new Branch(
                                        FEATURE,
                                        false,
                                        new Sha("ffff"),
                                        Instant.EPOCH,
                                        Option.none(),
                                        false,
                                        false,
                                        new Tracking.None(),
                                        Option.of(Path.of("/repos/app-feature")))))
                .model()
                .openPopup(new ForceDeleteBranchPopup(FEATURE, 2, false));

        assertEquals(
                List.of(new ForceDeleteCmd(FEATURE)),
                Update.update(stale, new ForceDeleteMsg.Confirmed()).cmds());
    }

    @Test
    void whenNoCommitIsLostButForcingFailsTheFailureIsReportedAfterBothCommands() {
        GitFailure<ProcessError> locked =
                new GitFailure<>(new GitError.CommandFailed("ref is locked", 1), executed("git branch -D"));
        GitInfoProvider git = new StubGitProvider() {
            @Override
            public Either<ProcessError, Integer> countOnlyOn(BranchName name) {
                return Either.right(0);
            }

            @Override
            public Either<GitFailure<ProcessError>, List<ExecutedCommand>> forceDeleteLocalBranch(BranchName name) {
                return Either.left(locked);
            }
        };

        assertEquals(
                DELETE_BOTH.failedWith(locked.after(NOT_MERGED.commands())),
                DELETE_BOTH.run(env(deletedWith(git, NOT_MERGED))));
    }

    private static GitInfoProvider deletedWith(GitInfoProvider git, GitFailure<GitError> refusal) {
        return new StubGitProvider() {
            @Override
            public Either<GitFailure<GitError>, List<ExecutedCommand>> deleteBranch(
                    BranchName name, RemoteBranch remote, DeleteScope scope) {
                return Either.left(refusal);
            }

            @Override
            public Either<ProcessError, Integer> countOnlyOn(BranchName name) {
                return git.countOnlyOn(name);
            }

            @Override
            public Either<GitFailure<ProcessError>, List<ExecutedCommand>> forceDeleteLocalBranch(BranchName name) {
                return git.forceDeleteLocalBranch(name);
            }
        };
    }

    @Test
    void theCheckedOutBranchIsNotDeletedEvenWhenConfirmed() {
        Next confirmed = Update.update(
                model().openPopup(new ForceDeleteBranchPopup(MAIN, 1, false)), new ForceDeleteMsg.Confirmed());

        assertEquals(List.empty(), confirmed.cmds());
    }

    @Test
    void theForcedDeletionRunsGitBranchD() {
        assertEquals(
                new OperationMsg.Succeeded(
                        new ForceDeleteCmd(FEATURE), executed("git branch --delete --force -- feature")),
                new ForceDeleteCmd(FEATURE).run(env(git(Either.left(NOT_MERGED), Either.right(3)))));
        assertEquals(java.util.List.of("force feature"), ran);
    }

    @Test
    void thePopupSaysHowManyCommitsAreLost() {
        assertEquals(
                "3 commits on feature are on no other branch and will be lost. Delete anyway?",
                ForceDeleteDialog.SPEC.message().apply(new ForceDeleteBranchPopup(FEATURE, 3, false)));
        assertEquals(
                "1 commit on feature is on no other branch and will be lost. Delete anyway?",
                ForceDeleteDialog.SPEC.message().apply(new ForceDeleteBranchPopup(FEATURE, 1, false)));
        assertEquals(
                "Delete branch feature",
                ForceDeleteDialog.SPEC.title().apply(new ForceDeleteBranchPopup(FEATURE, 1, false)));
    }

    @Test
    void thePopupOfABranchWhoseUpstreamIsGoneSaysTheChangesMayHaveBeenMergedAlready() {
        assertEquals(
                "2 commits on feature are on no other branch; its upstream is gone, so if its pull request was squash"
                        + " merged, their changes are already on the branch it was merged into. Delete anyway?",
                ForceDeleteDialog.SPEC.message().apply(new ForceDeleteBranchPopup(FEATURE, 2, true)));
        assertEquals(
                "1 commit on feature is on no other branch; its upstream is gone, so if its pull request was squash"
                        + " merged, its changes are already on the branch it was merged into. Delete anyway?",
                ForceDeleteDialog.SPEC.message().apply(new ForceDeleteBranchPopup(FEATURE, 1, true)));
    }
}
