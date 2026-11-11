package net.vanfleteren.jazygit.feature.branch.checkout;

import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.time.Instant;
import net.vanfleteren.jazygit.feature.worktree.add.AddWorktreeCmd;
import net.vanfleteren.jazygit.feature.worktree.add.AddWorktreeMsg;
import net.vanfleteren.jazygit.feature.worktree.add.CatchUp;
import net.vanfleteren.jazygit.feature.worktree.add.NewWorktreeBranchPopup;
import net.vanfleteren.jazygit.feature.worktree.add.NewWorktreePopup;
import net.vanfleteren.jazygit.feature.worktree.add.WorktreeSource;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.git.Git;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.StubBranches;
import net.vanfleteren.jazygit.git.StubGit;
import net.vanfleteren.jazygit.git.StubWorktrees;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.load.LoadMsg;
import net.vanfleteren.jazygit.state.operation.Notice;
import net.vanfleteren.jazygit.state.popup.Popup;
import net.vanfleteren.jazygit.state.popup.PopupMsg;
import net.vanfleteren.jazygit.ui.NoticeText;
import org.junit.jupiter.api.Test;

/**
 * Checking a remote branch out in a new worktree, with {@code w} on the remote branches, from any worktree (ADR 109).
 */
class RemoteWorktreeUpdateTest {

    private static final BranchName MAIN_BRANCH = new BranchName("main");
    private static final BranchName FEAT = new BranchName("feat");
    private static final BranchName MINE = new BranchName("mine");
    private static final RemoteBranch ORIGIN_FEAT = new RemoteBranch("origin", FEAT);
    private static final Worktree MAIN = new Worktree(
            Path.of("/repos/app"),
            Option.of(new Head.OnBranch(MAIN_BRANCH, Option.of(new Sha("aaaa")))),
            true,
            true,
            Option.none(),
            false);
    private static final Path ELSEWHERE = Path.of("/repos/app-elsewhere");

    /**
     * The main worktree shown, {@code main} checked out in it, {@code origin/feat} on the remote, and {@code others}
     * as the other local branches.
     */
    private static Model shown(Branch... others) {
        Model branches = TestModels.localBranchesRead(
                        loaded(),
                        List.of(Fixtures.branch(MAIN_BRANCH, true, new Sha("aaaa")))
                                .appendAll(List.of(others)))
                .model();
        Model remotes = TestModels.remoteBranchesRead(
                        branches, List.of(new RemoteRef(ORIGIN_FEAT, new Sha("ffff"), Instant.EPOCH)))
                .model();
        return Update.update(remotes, new LoadMsg.WorktreesLoaded(List.of(MAIN)))
                .model();
    }

    /**
     * {@code name}, following {@code origin/feat}, {@code ahead} and {@code behind} of it, checked out here when
     * {@code current}, or in the worktree at {@code worktree}.
     */
    private static Branch follower(BranchName name, int ahead, int behind, boolean current, Option<Path> worktree) {
        return new Branch(
                name,
                current,
                new Sha("1111"),
                Instant.EPOCH,
                Option.none(),
                true,
                false,
                new Tracking.Tracks(new Upstream(new BranchRef.Remote(ORIGIN_FEAT), ahead, behind)),
                worktree);
    }

    private static Branch idleFollower(BranchName name, int ahead, int behind) {
        return follower(name, ahead, behind, false, Option.none());
    }

    private static Option<Popup> asked(Model model) {
        return Update.update(model, new CheckoutMsg.RemoteInNewWorktreeRequested(ORIGIN_FEAT))
                .model()
                .popup();
    }

    @Test
    void withoutALocalBranchOneIsMadeUnderItsNameFollowingIt() {
        assertEquals(
                Option.of(new NewWorktreePopup(
                        new WorktreeSource.Created(FEAT, new NewBranchStart.Following(ORIGIN_FEAT)),
                        "/repos/app-feat")),
                asked(shown()));
    }

    @Test
    void aFollowerThatIsUpToDateIsCheckedOutAsItIs() {
        assertEquals(
                Option.of(
                        new NewWorktreePopup(new WorktreeSource.Existing(MINE, new CatchUp.AsIs()), "/repos/app-mine")),
                asked(shown(idleFollower(MINE, 0, 0))));
    }

    @Test
    void aFollowerThatIsOnlyBehindIsFastForwardedFirst() {
        WorktreeSource source = new WorktreeSource.Existing(MINE, new CatchUp.FastForward(ORIGIN_FEAT));
        Model asked = Update.update(
                        shown(idleFollower(MINE, 0, 2)), new CheckoutMsg.RemoteInNewWorktreeRequested(ORIGIN_FEAT))
                .model();

        assertEquals(Option.of(new NewWorktreePopup(source, "/repos/app-mine")), asked.popup());
        assertEquals(
                List.of(new AddWorktreeCmd(source, "/repos/app-mine")),
                Update.update(asked, new PopupMsg.Confirmed(NewWorktreePopup.class))
                        .cmds());
    }

    @Test
    void aFollowerWithCommitsOfItsOwnIsKeptAsItIsWhichIsSaid() {
        WorktreeSource source = new WorktreeSource.Existing(MINE, new CatchUp.Kept(ORIGIN_FEAT, 1));
        Model model = shown(idleFollower(MINE, 1, 3));
        assertEquals(Option.of(new NewWorktreePopup(source, "/repos/app-mine")), asked(model));

        Next added = Update.update(
                model,
                new AddWorktreeMsg.Added(
                        new AddWorktreeCmd(source, "/repos/app-mine"), "/repos/app-mine", List.empty()));

        Option<Notice> notice = added.model().outcome().notice();
        assertEquals(Option.of(new Notice.BranchKept(MINE, 1, ORIGIN_FEAT)), notice);
        assertEquals("mine kept: 1 commit not on origin/feat.", NoticeText.of(notice.get()));
    }

    @Test
    void theFollowerUnderItsNameThatIsFreeWinsOverOthers() {
        assertEquals(
                Option.of(new NewWorktreePopup(new WorktreeSource.Existing(FEAT), "/repos/app-feat")),
                asked(shown(idleFollower(new BranchName("a-first"), 0, 0), idleFollower(FEAT, 0, 0))));
    }

    @Test
    void aFreeFollowerWinsOverOnesCheckedOutHereOrElsewhere() {
        Model model = shown(
                follower(FEAT, 0, 0, false, Option.of(ELSEWHERE)),
                follower(new BranchName("feat-here"), 0, 0, true, Option.none()),
                idleFollower(new BranchName("feat-2"), 0, 0),
                idleFollower(new BranchName("feat-1"), 0, 0));

        assertEquals(
                Option.of(new NewWorktreePopup(
                        new WorktreeSource.Existing(new BranchName("feat-1")), "/repos/app-feat-1")),
                asked(model));
    }

    @Test
    void aFollowerOnlyCheckedOutInAnotherWorktreeOffersToSwitchThere() {
        assertEquals(
                Option.of(new SwitchWorktreePopup(MINE, ELSEWHERE.toString())),
                asked(shown(follower(MINE, 0, 0, false, Option.of(ELSEWHERE)))));
    }

    @Test
    void aFollowerOnlyCheckedOutHereAsksForANewBranchFollowingIt() {
        Model model = TestModels.localBranchesRead(shown(), List.of(follower(MINE, 0, 0, true, Option.none())))
                .model();

        assertEquals(
                Option.of(new NewWorktreeBranchPopup("origin/feat", "feat", new NewBranchStart.Following(ORIGIN_FEAT))),
                asked(model));
    }

    @Test
    void aLocalBranchOfItsNameThatFollowsSomethingElseAsksForAName() {
        assertEquals(
                Option.of(new NewWorktreeBranchPopup("origin/feat", "feat", new NewBranchStart.Following(ORIGIN_FEAT))),
                asked(shown(Fixtures.branch(FEAT, false, new Sha("2222")))));
    }

    @Test
    void anUnknownRemoteBranchDoesNothing() {
        assertEquals(
                Option.none(),
                Update.update(
                                shown(),
                                new CheckoutMsg.RemoteInNewWorktreeRequested(
                                        new RemoteBranch("origin", new BranchName("nowhere"))))
                        .model()
                        .popup());
    }

    @Test
    void spaceInALinkedWorktreeFastForwardsTheFollowerInANewWorktreeToo() {
        Worktree main = new Worktree(MAIN.path(), MAIN.head(), true, false, Option.none(), false);
        Worktree linked = new Worktree(
                Path.of("/repos/app-agent"),
                Option.of(new Head.OnBranch(new BranchName("agent"), Option.of(new Sha("3333")))),
                false,
                true,
                Option.none(),
                false);
        Model inLinked = Update.update(
                        shown(idleFollower(MINE, 0, 2)), new LoadMsg.WorktreesLoaded(List.of(main, linked)))
                .model();

        assertEquals(
                Option.of(new CheckoutRequest.Local(MINE, new CatchUp.FastForward(ORIGIN_FEAT))),
                Update.update(inLinked, new CheckoutMsg.RemoteRequested(ORIGIN_FEAT))
                        .model()
                        .popup(CheckoutWherePopup.class)
                        .map(CheckoutWherePopup::request));
    }

    @Test
    void theFastForwardRunsBeforeTheWorktreeIsAddedAndIsSkippedOtherwise() {
        java.util.List<String> ran = new java.util.ArrayList<>();
        Git git = StubGit.of()
                .withBranches(new StubBranches() {
                    @Override
                    public Either<GitFailure, List<ExecutedCommand>> catchUp(List<CaughtUpBranch> branches) {
                        branches.forEach(
                                branch -> ran.add("catch up " + branch.branch().value() + " to "
                                        + branch.upstream().shortName()));
                        return Either.right(List.empty());
                    }
                })
                .withWorktrees(new StubWorktrees() {
                    @Override
                    public Either<GitFailure, List<ExecutedCommand>> add(Path location, BranchName branch) {
                        ran.add("add " + branch.value());
                        return Either.right(List.empty());
                    }
                });
        Env env = new Env(git, StubGitHub.empty());

        new AddWorktreeCmd(new WorktreeSource.Existing(MINE, new CatchUp.FastForward(ORIGIN_FEAT)), "/repos/app-mine")
                .run(env);
        new AddWorktreeCmd(new WorktreeSource.Existing(FEAT, new CatchUp.Kept(ORIGIN_FEAT, 1)), "/repos/app-feat")
                .run(env);

        assertEquals(java.util.List.of("catch up mine to origin/feat", "add mine", "add feat"), ran);
    }
}
