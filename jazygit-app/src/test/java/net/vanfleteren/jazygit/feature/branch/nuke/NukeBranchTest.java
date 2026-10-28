package net.vanfleteren.jazygit.feature.branch.nuke;

import static net.vanfleteren.jazygit.state.TestModels.errorText;
import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.git.StubGitProvider;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.LoadMsg;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.OperationMsg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

/**
 * Nuking a branch (ADR 43): what is asked, what is refused, and what the command deletes when a step fails.
 */
class NukeBranchTest {

    private static final BranchName MAIN = new BranchName("main");
    private static final BranchName FEATURE = new BranchName("feature");
    private static final BranchName TRUNK = new BranchName("trunk");
    private static final RemoteBranch ORIGIN_FEATURE = new RemoteBranch("origin", FEATURE);
    private static final Worktree APP = worktree("/repos/app", MAIN, true, true, Option.none());
    private static final Worktree LINKED = worktree("/repos/app-feature", FEATURE, false, false, Option.of(""));

    private static Worktree worktree(
            String path, BranchName branch, boolean main, boolean current, Option<String> locked) {
        return new Worktree(
                Path.of(path),
                Option.of(new Head.OnBranch(branch, Option.of(new Sha("aaaa")))),
                main,
                current,
                locked,
                false);
    }

    private static Branch branch(BranchName name, boolean current, boolean isDefault, Option<RemoteBranch> upstream) {
        return branch(name, current, isDefault, upstream, Option.none());
    }

    private static Branch branch(
            BranchName name, boolean current, boolean isDefault, Option<RemoteBranch> upstream, Option<Path> worktree) {
        return new Branch(
                name,
                current,
                new Sha("ffff"),
                Instant.EPOCH,
                Option.none(),
                upstream.isDefined(),
                isDefault,
                upstream.<Tracking>map(remote -> new Tracking.Tracks(new Upstream(new BranchRef.Remote(remote), 0, 0)))
                        .getOrElse(Tracking.None::new),
                worktree);
    }

    /**
     * {@code main}, the default branch, checked out in the main worktree, which is shown; {@code feature}, on
     * {@code origin}, checked out in a locked linked worktree; and {@code others}.
     */
    private static Model model(Branch... others) {
        return model(List.of(APP, LINKED), others);
    }

    private static Model model(List<Worktree> worktrees, Branch... others) {
        Model branches = TestModels.localBranchesRead(
                        loaded(),
                        List.of(
                                        branch(MAIN, true, true, Option.of(new RemoteBranch("origin", MAIN))),
                                        branch(
                                                FEATURE,
                                                false,
                                                false,
                                                Option.of(ORIGIN_FEATURE),
                                                Option.of(LINKED.path())))
                                .appendAll(List.of(others)))
                .model();
        return Update.update(branches, new LoadMsg.WorktreesLoaded(worktrees)).model();
    }

    @Test
    void oneDialogNamesTheBranchItsRemoteBranchAndItsWorktreeAndConfirmingNukesThemAll() {
        Next asked = Update.update(model(), new NukeBranchMsg.Requested(FEATURE));

        NukeBranchPopup popup = new NukeBranchPopup(FEATURE, Option.of(ORIGIN_FEATURE), Option.of(LINKED));
        assertEquals(Option.of(popup), asked.model().popup());
        assertEquals(
                "NUKE IT? This deletes, with force and without asking anything else:\n"
                        + "- local branch feature\n"
                        + "- remote branch origin/feature\n"
                        + "- worktree /repos/app-feature (locked), with its changes\n"
                        + "Commits that only this branch has, and uncommitted changes in its worktree, are lost for"
                        + " good. This cannot be undone.",
                NukeBranchDialogs.NUKE_BRANCH.message().apply(popup));
        assertEquals(
                List.of(new NukeBranchCmd(FEATURE, Option.of("/repos/app-feature"), Option.of(ORIGIN_FEATURE))),
                Update.update(asked.model(), new NukeBranchMsg.Confirmed()).cmds());
    }

    @Test
    void aRemoteBranchOfAnotherNameOrOnAnotherRemoteIsLeftAlone() {
        BranchName fix = new BranchName("fix");
        BranchName develop = new BranchName("develop");
        BranchName local = new BranchName("local");
        Model model = model(
                branch(fix, false, false, Option.of(new RemoteBranch("origin", MAIN))),
                branch(develop, false, false, Option.of(new RemoteBranch("upstream", develop))),
                branch(local, false, false, Option.none()));

        for (BranchName name : List.of(fix, develop, local)) {
            Model asked =
                    Update.update(model, new NukeBranchMsg.Requested(name)).model();
            assertEquals(Option.of(new NukeBranchPopup(name, Option.none(), Option.none())), asked.popup());
            assertEquals(
                    List.of(new NukeBranchCmd(name, Option.none(), Option.none())),
                    Update.update(asked, new NukeBranchMsg.Confirmed()).cmds());
        }
    }

    @Test
    void theDefaultBranchAndOneCheckedOutHereOrInTheMainWorktreeAreRefused() {
        Worktree shownLinked = worktree("/repos/app-feature", FEATURE, false, true, Option.none());
        Worktree mainElsewhere = worktree("/repos/app", TRUNK, true, false, Option.none());
        Model inLinked = model(
                List.of(mainElsewhere, shownLinked),
                branch(TRUNK, false, false, Option.none(), Option.of(mainElsewhere.path())));
        BranchName released = new BranchName("released");

        assertEquals(
                Option.of("main is the default branch and cannot be deleted"),
                errorText(Update.update(model(), new NukeBranchMsg.Requested(MAIN))
                        .model()));
        assertEquals(
                Option.of("Worktree app is the one shown; switch to another one to remove it"),
                errorText(Update.update(
                                model(branch(released, true, false, Option.none())),
                                new NukeBranchMsg.Requested(released))
                        .model()));
        assertEquals(
                Option.of("Worktree app is the main one, which holds the repository; it cannot be removed"),
                errorText(Update.update(inLinked, new NukeBranchMsg.Requested(TRUNK))
                        .model()));
    }

    @Test
    void aBranchOrRemoteBranchThatAnOperationRunsOnIsNotAskedAbout() {
        assertEquals(
                Option.none(),
                Update.update(
                                TestModels.busy(model(), new BranchRef.Local(FEATURE), NukeBranchCmd.NUKE_BRANCH),
                                new NukeBranchMsg.Requested(FEATURE))
                        .model()
                        .popup());
        assertEquals(
                Option.none(),
                Update.update(
                                TestModels.busy(
                                        model(), new BranchRef.Remote(ORIGIN_FEATURE), NukeBranchCmd.NUKE_BRANCH),
                                new NukeBranchMsg.Requested(FEATURE))
                        .model()
                        .popup());
    }

    @Test
    void nothingStartsWhenItsWorktreeChangedSinceTheDialogNamedIt() {
        Model asked =
                Update.update(model(), new NukeBranchMsg.Requested(FEATURE)).model();
        Worktree moved = worktree("/repos/elsewhere", FEATURE, false, false, Option.none());
        Model changed = Update.update(
                        TestModels.localBranchesRead(
                                        asked,
                                        List.of(
                                                branch(MAIN, true, true, Option.none()),
                                                branch(
                                                        FEATURE,
                                                        false,
                                                        false,
                                                        Option.of(ORIGIN_FEATURE),
                                                        Option.of(moved.path()))))
                                .model(),
                        new LoadMsg.WorktreesLoaded(List.of(APP, moved)))
                .model();

        assertEquals(
                List.empty(),
                Update.update(changed, new NukeBranchMsg.Confirmed()).cmds());
    }

    @Test
    void theStepsRunInOrderAndTheirCommandsAreLoggedTogether() {
        java.util.List<String> ran = new java.util.ArrayList<>();
        GitInfoProvider git = new StubGitProvider() {
            @Override
            public Either<GitFailure<ProcessError>, List<ExecutedCommand>> removeWorktreeAnyway(Path location) {
                ran.add("remove " + location);
                return StubGitProvider.ran("git worktree remove --force --force " + location);
            }

            @Override
            public Either<GitFailure<ProcessError>, List<ExecutedCommand>> forceDeleteLocalBranch(BranchName name) {
                ran.add("delete " + name.value());
                return StubGitProvider.ran("git branch -D " + name.value());
            }

            @Override
            public Either<GitFailure<ProcessError>, List<ExecutedCommand>> deleteRemoteBranch(RemoteBranch remote) {
                ran.add("delete " + remote.shortName());
                return StubGitProvider.ran(
                        "git push origin --delete refs/heads/" + remote.branch().value());
            }
        };
        NukeBranchCmd cmd = new NukeBranchCmd(FEATURE, Option.of("/repos/app-feature"), Option.of(ORIGIN_FEATURE));

        Msg done = cmd.run(new Env(git, StubGitHub.empty()));

        assertEquals(java.util.List.of("remove /repos/app-feature", "delete feature", "delete origin/feature"), ran);
        OperationMsg.Succeeded succeeded = assertInstanceOf(OperationMsg.Succeeded.class, done);
        assertEquals(3, succeeded.commands().size());
        assertEquals(Option.of(Cmd.Guard.REMOTE_REFS), cmd.guard());
        assertEquals(Option.none(), new NukeBranchCmd(FEATURE, Option.none(), Option.none()).guard());
    }

    @Test
    void whenTheWorktreeStaysTheBranchStaysButItsRemoteBranchIsDeletedAndWhatIsLeftIsSaid() {
        java.util.List<String> ran = new java.util.ArrayList<>();
        GitInfoProvider git = new StubGitProvider() {
            @Override
            public Either<GitFailure<ProcessError>, List<ExecutedCommand>> removeWorktreeAnyway(Path location) {
                return StubGitProvider.refused("git worktree remove --force --force " + location, "Permission denied");
            }

            @Override
            public Either<GitFailure<ProcessError>, List<ExecutedCommand>> forceDeleteLocalBranch(BranchName name) {
                ran.add("delete " + name.value());
                return StubGitProvider.ran();
            }

            @Override
            public Either<GitFailure<ProcessError>, List<ExecutedCommand>> deleteRemoteBranch(RemoteBranch remote) {
                ran.add("delete " + remote.shortName());
                return StubGitProvider.ran("git push origin --delete refs/heads/feature");
            }
        };
        NukeBranchCmd cmd = new NukeBranchCmd(FEATURE, Option.of("/repos/app-feature"), Option.of(ORIGIN_FEATURE));

        Msg done = cmd.run(new Env(git, StubGitHub.empty()));

        assertEquals(java.util.List.of("delete origin/feature"), ran);
        NukeBranchMsg.NotAllNuked notAll = assertInstanceOf(NukeBranchMsg.NotAllNuked.class, done);
        assertEquals(List.of("/repos/app-feature", "feature"), notAll.left());
        assertEquals(2, notAll.commands().size());
        assertEquals(
                Option.of("Nuking branch feature left /repos/app-feature, feature: Permission denied"),
                errorText(Update.update(model(), done).model()));
        assertEquals(2, cmd.perform(git).getLeft().commands().size());
    }
}
