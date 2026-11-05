package net.vanfleteren.jazygit.feature.branch.stack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.time.Instant;
import net.vanfleteren.jazygit.feature.commit.create.CommitPopup;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.State;
import net.vanfleteren.jazygit.gh.model.PullRequestStack;
import net.vanfleteren.jazygit.gh.model.PullRequestStack.Entry;
import net.vanfleteren.jazygit.git.Git;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.StubGit;
import net.vanfleteren.jazygit.git.StubHistory;
import net.vanfleteren.jazygit.git.StubIntegrate;
import net.vanfleteren.jazygit.git.StubRemotes;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.CommitMessage;
import net.vanfleteren.jazygit.git.model.Fixtures;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.RemoteName;
import net.vanfleteren.jazygit.git.model.RemoteRef;
import net.vanfleteren.jazygit.git.model.Revision;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.StackLayer;
import net.vanfleteren.jazygit.git.model.Tracking;
import net.vanfleteren.jazygit.git.model.Upstream;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.AppError.StackRefusal;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Notice;
import net.vanfleteren.jazygit.state.OperationMsg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import org.junit.jupiter.api.Test;

/**
 * Rebasing a stack on GitHub in one rebase, from its menu, and pushing it after (ADR 74).
 */
class RebaseStackTest {

    private static final BranchName MAIN = new BranchName("main");
    private static final BranchName C = new BranchName("feat/c");
    private static final BranchName D = new BranchName("feat/d");
    private static final Sha C_TIP = new Sha("cccc");
    private static final Revision ORIGIN_MAIN = new BranchRef.Remote(new RemoteBranch("origin", MAIN));

    private static Branch branch(BranchName name, Sha tip) {
        return Fixtures.branch(name, false, tip);
    }

    private static PullRequestStack stack(List<Entry> entries, int position) {
        return new PullRequestStack(12, position, entries.size(), entries);
    }

    private static final List<Entry> ENTRIES =
            List.of(new Entry(1, 98, State.OPEN, "feat/c", "main"), new Entry(2, 99, State.OPEN, "feat/d", "feat/c"));

    private static PullRequest pr(int number, String head, List<Entry> entries, int position) {
        return new PullRequest(
                number, head, "", false, Checks.NONE, Review.NONE, Option.of(stack(entries, position)), Option.none());
    }

    // main checked out; feat/c and feat/d make stack #12 on main, which is on origin.
    private static Model model(List<Branch> local, List<Entry> entries) {
        Model read = TestModels.localBranchesRead(TestModels.loaded(), local).model();
        Model remotes = TestModels.remoteBranchesRead(
                        read, List.of(new RemoteRef(new RemoteBranch("origin", MAIN), new Sha("0000"), Instant.EPOCH)))
                .model();
        return remotes.mapGitHub(gitHub -> gitHub.withPullRequests(Option.of(HashMap.of(
                C, pr(98, "feat/c", entries, 1),
                D, pr(99, "feat/d", entries, 2)))));
    }

    private static final List<Branch> LOCAL = List.of(
            Fixtures.branch(MAIN, true, new Sha("0000"), Instant.EPOCH, true, true),
            branch(C, C_TIP),
            branch(D, new Sha("dddd")));

    private static final Model MODEL = model(LOCAL, ENTRIES);

    private static Either<AppError, StackRebase> plan(Model model, List<Entry> entries) {
        return StackRebase.of(model, stack(entries, 1));
    }

    private static Option<StackRefusal> refusal(Model model, List<Entry> entries) {
        return plan(model, entries).swap().toOption().map(error -> ((AppError.StackNotRebased) error).why());
    }

    @Test
    void eachBranchIsToldApartFromWhereTheOneBelowWasAndTheBottomFromTheBaseOnOrigin() {
        StackRebase rebase = plan(MODEL, ENTRIES).get();

        assertEquals(ORIGIN_MAIN, rebase.base());
        assertEquals(
                List.of(new StackLayer(C, List.of(ORIGIN_MAIN)), new StackLayer(D, List.of(new BranchRef.Local(C)))),
                rebase.layers());
        assertEquals(Option.of(MAIN), rebase.back());
    }

    @Test
    void aBranchInAnotherWorktreeIsNotMovedUnderItsFiles() {
        Branch elsewhere = new Branch(
                D,
                false,
                new Sha("dddd"),
                Instant.EPOCH,
                Option.none(),
                false,
                false,
                new Tracking.None(),
                Option.of(Path.of("/elsewhere")));

        assertEquals(
                Option.of(StackRefusal.OTHER_WORKTREE), refusal(model(LOCAL.update(2, elsewhere), ENTRIES), ENTRIES));
    }

    @Test
    void aBranchInAMergeQueueIsNotRewritten() {
        List<Entry> queued = ENTRIES.update(1, new Entry(2, 99, State.OPEN, "feat/d", "feat/c", Option.none(), true));

        assertEquals(Option.of(StackRefusal.MERGE_QUEUE), refusal(model(LOCAL, queued), queued));
    }

    // feat/d follows origin/feat/d, which is two commits ahead of it, at d2d2.
    private static final Branch BEHIND = Fixtures.branch(
            D,
            false,
            new Sha("dddd"),
            Instant.EPOCH,
            true,
            false,
            Option.of(new Upstream(new BranchRef.Remote(new RemoteBranch("origin", D)), 0, 2)));

    // Whether feat/d ever had what is on origin.
    private static Git everHad(boolean had) {
        return StubGit.of().withHistory(new StubHistory() {
            @Override
            public Either<GitError, Boolean> everHad(BranchName branch, Revision revision) {
                return Either.right(
                        !branch.equals(D) || !revision.equals(new RemoteBranch("origin", D).tracking()) || had);
            }
        });
    }

    // As model, with feat/d behind origin/feat/d.
    private static Model behindOrigin() {
        return TestModels.remoteBranchesRead(
                        model(LOCAL.update(2, BEHIND), ENTRIES),
                        List.of(
                                new RemoteRef(new RemoteBranch("origin", MAIN), new Sha("0000"), Instant.EPOCH),
                                new RemoteRef(new RemoteBranch("origin", D), new Sha("d2d2"), Instant.EPOCH)))
                .model();
    }

    @Test
    void aBranchBehindWhatSomeoneElsePushedIsPulledFirst() {
        StackRebase rebase = plan(behindOrigin(), ENTRIES).get();

        Msg read = new StackRebaseCmd.ReadStack(rebase).run(new Env(everHad(false), StubGitHub.empty()));

        assertEquals(new StackMsg.StackBehindRemote(12, D, new RemoteBranch("origin", D)), read);
        assertEquals(
                Option.of("Stack #12 is not rebased: feat/d is behind what was pushed to it; pull it first"),
                TestModels.errorText(Update.update(behindOrigin(), read).model()));
    }

    // feat/d was amended here after it was pushed: it is behind origin, but had what is there.
    @Test
    void aBranchRewrittenHereIsRebasedThoughItIsBehindItsUpstream() {
        StackRebase rebase = plan(behindOrigin(), ENTRIES).get();

        assertInstanceOf(
                StackMsg.StackRead.class,
                new StackRebaseCmd.ReadStack(rebase).run(new Env(everHad(true), StubGitHub.empty())));
    }

    // As model, with feat/d on origin at originD too.
    private static Model withOriginD(Sha originD) {
        return TestModels.remoteBranchesRead(
                        MODEL,
                        List.of(
                                new RemoteRef(new RemoteBranch("origin", MAIN), new Sha("0000"), Instant.EPOCH),
                                new RemoteRef(new RemoteBranch("origin", D), originD, Instant.EPOCH)))
                .model();
    }

    @Test
    void aBranchWithoutAnUpstreamElsewhereOnOriginIsAskedAboutBeforeTheRebase() {
        assertEquals(
                List.of(Tuple.of(D, new RemoteBranch("origin", D))),
                plan(withOriginD(new Sha("d2d2")), ENTRIES).get().elsewhere());
        assertEquals(
                List.empty(), plan(withOriginD(new Sha("dddd")), ENTRIES).get().elsewhere());
    }

    @Test
    void aBranchWithoutAnUpstreamBehindOriginIsMadeToFollowItAndPulledFirst() {
        Model model = withOriginD(new Sha("d2d2"));
        StackRebase rebase = plan(model, ENTRIES).get();

        Msg read = new StackRebaseCmd.ReadStack(rebase).run(new Env(everHad(false), StubGitHub.empty()));

        assertEquals(new StackMsg.StackBehindRemote(12, D, new RemoteBranch("origin", D)), read);
        assertEquals(
                Option.of("Stack #12 is not rebased: feat/d is behind what was pushed to it, which it does not follow;"
                        + " set that as its upstream, then pull it"),
                TestModels.errorText(Update.update(model, read).model()));
    }

    @Test
    void anOpenPullRequestWithoutALocalBranchCannotBeMoved() {
        List<Entry> three = ENTRIES.append(new Entry(3, 100, State.OPEN, "feat/e", "feat/d"));

        assertEquals(Option.of(StackRefusal.NOT_CHECKED_OUT), refusal(model(LOCAL, three), three));
    }

    @Test
    void aMergedBottomIsLeftOutFromWhereGitHubSaysItWas() {
        List<Entry> merged = List.of(
                new Entry(
                        1,
                        97,
                        State.MERGED,
                        "feat/b",
                        "main",
                        Option.of("b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0"),
                        false),
                new Entry(2, 98, State.OPEN, "feat/c", "feat/b"),
                new Entry(3, 99, State.OPEN, "feat/d", "feat/c"));

        assertEquals(
                new StackLayer(C, List.of(new Revision.Commit(new Sha("b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0")))),
                plan(model(LOCAL, merged), merged).get().layers().head());
    }

    // GitHub says where feat/b was, origin still has it, and so does the local branch: none of their commits is
    // feat/c's own.
    @Test
    void aMergedBottomIsLeftOutFromAllThatIsKnownOfWhereItWas() {
        BranchName b = new BranchName("feat/b");
        List<Entry> merged = List.of(
                new Entry(
                        1,
                        97,
                        State.MERGED,
                        "feat/b",
                        "main",
                        Option.of("b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0"),
                        false),
                new Entry(2, 98, State.OPEN, "feat/c", "feat/b"),
                new Entry(3, 99, State.OPEN, "feat/d", "feat/c"));
        Model known = TestModels.remoteBranchesRead(
                        model(LOCAL.append(branch(b, new Sha("b1b1"))), merged),
                        List.of(
                                new RemoteRef(new RemoteBranch("origin", MAIN), new Sha("0000"), Instant.EPOCH),
                                new RemoteRef(new RemoteBranch("origin", b), new Sha("b2b2"), Instant.EPOCH)))
                .model();

        assertEquals(
                new StackLayer(
                        C,
                        List.of(
                                new Revision.Commit(new Sha("b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0")),
                                new RemoteBranch("origin", b).tracking(),
                                new BranchRef.Local(b))),
                plan(known, merged).get().layers().head());
    }

    @Test
    void aBranchIsToldApartFromTheOneBelowAlsoWhereThatWasPushed() {
        Model pushed = TestModels.remoteBranchesRead(
                        MODEL,
                        List.of(
                                new RemoteRef(new RemoteBranch("origin", MAIN), new Sha("0000"), Instant.EPOCH),
                                new RemoteRef(new RemoteBranch("origin", C), C_TIP, Instant.EPOCH)))
                .model();

        assertEquals(
                new StackLayer(D, List.of(new BranchRef.Local(C), new RemoteBranch("origin", C).tracking())),
                plan(pushed, ENTRIES).get().layers().last());
    }

    @Test
    void changesThatAreNotCommittedAreCommittedOrStashedFirst() {
        Model dirty = MODEL.mapRepo(repo -> repo.withStatus(new Loadable.Loaded<>(TestModels.DIRTY)));

        assertEquals(Option.of(StackRefusal.CHANGES), refusal(dirty, ENTRIES));
        assertEquals(
                Option.of("Stack #12 is not rebased: the worktree has changes; commit or stash them first"),
                TestModels.errorText(dirty.fail(plan(dirty, ENTRIES).getLeft())));
    }

    @Test
    void aBaseNotFetchedFromTheRemoteIsNotTakenFromTheLocalBranch() {
        Model unfetched = TestModels.remoteBranchesRead(MODEL, List.empty()).model();

        assertEquals(Option.of(StackRefusal.BASE_NOT_FETCHED), refusal(unfetched, ENTRIES));
    }

    @Test
    void branchesPushedToDifferentRemotesAreNotPushedInOne() {
        Branch forked = Fixtures.branch(
                D,
                false,
                new Sha("dddd"),
                Instant.EPOCH,
                true,
                false,
                Option.of(new Upstream(new BranchRef.Remote(new RemoteBranch("fork", D)), 0, 0)));

        assertEquals(Option.of(StackRefusal.OTHER_REMOTE), refusal(model(LOCAL.update(2, forked), ENTRIES), ENTRIES));
    }

    // mine follows origin/feat/d, the branch of #99: it is pushed there, not to a branch of its own name.
    @Test
    void aBranchIsPushedToTheBranchOfItsPullRequest() {
        BranchName mine = new BranchName("mine");
        Branch following = Fixtures.branch(
                mine,
                false,
                new Sha("dddd"),
                Instant.EPOCH,
                true,
                false,
                Option.of(new Upstream(new BranchRef.Remote(new RemoteBranch("origin", D)), 0, 0)));

        StackPush push = StackPush.of(model(LOCAL.update(2, following), ENTRIES), stack(ENTRIES, 1))
                .get();

        assertEquals(List.of(Tuple.of(C, C), Tuple.of(mine, D)), push.pushed());
    }

    @Test
    void aMergedBottomNobodyKnowsWhereItWasRefuses() {
        List<Entry> merged = List.of(
                new Entry(1, 97, State.MERGED, "feat/b", "main"),
                new Entry(2, 98, State.OPEN, "feat/c", "feat/b"),
                new Entry(3, 99, State.OPEN, "feat/d", "feat/c"));

        assertEquals(Option.of(StackRefusal.MERGED_UNKNOWN), refusal(model(LOCAL, merged), merged));
    }

    @Test
    void theMenuReadsTheCommitsThenAsksSayingWhatMergesAreDropped() {
        Model menu = Update.update(MODEL, new StackMsg.MenuRequested(Option.none(), Option.of(12)))
                .model();

        Update.Next chosen = Update.update(menu, new StackMsg.RebaseChosen());
        StackRebase rebase = plan(MODEL, ENTRIES).get();
        assertEquals(List.of(new StackRebaseCmd.ReadStack(rebase)), chosen.cmds());

        List<StackLayer.Commits> commits = List.of(
                new StackLayer.Commits(C, C_TIP, List.of(new Sha("c1c1")), List.empty()),
                new StackLayer.Commits(
                        D,
                        new Sha("dddd"),
                        List.of(new Sha("d1d1")),
                        List.of(new StackLayer.Merge(new Sha("eeee"), "Merge main"))));
        Model asking = Update.update(chosen.model(), new StackMsg.StackRead(rebase, commits))
                .model();

        StackPopups.RebaseConfirm popup =
                asking.popup(StackPopups.RebaseConfirm.class).get();
        assertEquals(new StackRebaseCmd.RebaseStack(rebase, commits, true), popup.cmd());
        assertEquals(
                "Puts feat/c, feat/d each on the one below, the bottom on origin/main, in one rebase; then "
                        + "force-pushes them, all or none.\n\n"
                        + "feat/d: its merge \"Merge main\" is dropped",
                StackDialogs.rebased(popup));

        // Enter rebases and pushes; l only rebases.
        assertEquals(
                List.of(popup.cmd()),
                Update.update(asking, new Msg.PopupConfirmed(StackPopups.RebaseConfirm.class))
                        .cmds());
        assertEquals(
                List.of(popup.cmd().locally()),
                Update.update(asking, new StackMsg.RebaseLocallyChosen()).cmds());
    }

    @Test
    void aStackReadWhileAnotherDialogIsOpenLeavesItAndSaysToRebaseFromTheMenu() {
        // As after linking and rebasing, when GitHub lists the stack only once the user started a commit.
        CommitPopup typing = new CommitPopup(new CommitMessage("Half a sum", ""));
        StackRebase rebase = plan(MODEL, ENTRIES).get();

        Update.Next read = Update.update(
                MODEL.openPopup(typing),
                new StackMsg.StackRead(
                        rebase, List.of(new StackLayer.Commits(C, C_TIP, List.of(new Sha("c1c1")), List.empty()))));

        assertEquals(Option.of(typing), read.model().popup(CommitPopup.class));
        assertEquals(
                Option.of(new Notice.StackNotAsked(12)), read.model().outcome().notice());
        assertEquals(List.empty(), read.cmds());
    }

    @Test
    void aRefusedRebaseSaysWhy() {
        List<Entry> queued = ENTRIES.update(1, new Entry(2, 99, State.OPEN, "feat/d", "feat/c", Option.none(), true));
        Model menu = Update.update(model(LOCAL, queued), new StackMsg.MenuRequested(Option.none(), Option.of(12)))
                .model();

        Update.Next chosen = Update.update(menu, new StackMsg.RebaseChosen());

        assertEquals(List.empty(), chosen.cmds());
        assertEquals(
                Option.of("Stack #12 is not rebased: feat/d is in a merge queue"),
                TestModels.errorText(chosen.model()));
    }

    // Records the branches it is asked to push.
    private static Git pushingInto(java.util.List<List<Tuple2<BranchName, BranchName>>> pushed) {
        return StubGit.of().withRemotes(new StubRemotes() {
            @Override
            public Either<GitFailure, List<ExecutedCommand>> pushAll(
                    RemoteName remote, List<Tuple2<BranchName, BranchName>> branches) {
                pushed.add(branches);
                return Either.right(List.empty());
            }
        });
    }

    @Test
    void theRebaseChecksOutTheBranchThatWasThenPushesTheStackAtOnce() {
        StackRebaseCmd.RebaseStack cmd =
                new StackRebaseCmd.RebaseStack(plan(MODEL, ENTRIES).get(), List.empty(), true);
        java.util.List<List<Tuple2<BranchName, BranchName>>> pushed = new java.util.ArrayList<>();

        Msg done = cmd.run(new Env(pushingInto(pushed), StubGitHub.empty()));

        assertInstanceOf(OperationMsg.Succeeded.class, done);
        assertEquals(java.util.List.of(List.of(Tuple.of(C, C), Tuple.of(D, D))), pushed);
        assertEquals(true, cmd.changesGitHub());
        assertEquals(
                Option.none(),
                Update.update(TestModels.running(MODEL, cmd), done).model().popup());
    }

    @Test
    void theBranchesOfAStackAreBusyWhileItIsPushedOrRebased() {
        StackRebaseCmd.PushStack push = new StackRebaseCmd.PushStack(
                StackPush.of(MODEL, stack(ENTRIES, 1)).get());
        StackRebaseCmd.RebaseStack rebase =
                new StackRebaseCmd.RebaseStack(plan(MODEL, ENTRIES).get(), List.empty(), true);

        Model pushing = TestModels.running(MODEL, push);
        Model rebasing = TestModels.running(MODEL, rebase);

        assertEquals(
                io.vavr.collection.HashMap.of(
                        new BranchRef.Local(C), StackRebaseCmd.PUSH_STACK,
                        new BranchRef.Local(D), StackRebaseCmd.PUSH_STACK),
                pushing.jobs().busy());
        assertEquals(Option.none(), pushing.idleBranch(D));
        assertEquals(2, rebasing.jobs().busy().size());
    }

    @Test
    void aRebaseOnlyPushesNothing() {
        StackRebaseCmd.RebaseStack cmd =
                new StackRebaseCmd.RebaseStack(plan(MODEL, ENTRIES).get(), List.empty(), false);
        java.util.List<List<Tuple2<BranchName, BranchName>>> pushed = new java.util.ArrayList<>();

        cmd.run(new Env(pushingInto(pushed), StubGitHub.empty()));

        assertEquals(java.util.List.of(), pushed);
        assertEquals(false, cmd.changesGitHub());
    }

    @Test
    void aRebaseThatStopsOnAConflictChecksNothingOutAndPushesNothing() {
        StackRebaseCmd.RebaseStack cmd =
                new StackRebaseCmd.RebaseStack(plan(MODEL, ENTRIES).get(), List.empty(), true);
        Git stopping = StubGit.of().withIntegrate(new StubIntegrate() {
            @Override
            public Either<GitFailure, List<ExecutedCommand>> rebaseStack(
                    Revision base, List<StackLayer.Commits> layers) {
                return Either.left(
                        new GitFailure(new GitError.StoppedOnConflicts(Option.none(), 1), List.empty(), Option.none()));
            }
        });

        assertInstanceOf(OperationMsg.Stopped.class, cmd.run(new Env(stopping, StubGitHub.empty())));
    }

    @Test
    void pushingTheStackPushesItsOpenBranchesAtOnce() {
        Model menu = Update.update(MODEL, new StackMsg.MenuRequested(Option.none(), Option.of(12)))
                .model();

        assertEquals(
                List.of(new StackRebaseCmd.PushStack(
                        StackPush.of(MODEL, stack(ENTRIES, 1)).get())),
                Update.update(menu, new StackMsg.PushChosen()).cmds());
    }

    @Test
    void pushingAStackWithAPullRequestNotCheckedOutSaysSo() {
        List<Entry> three = ENTRIES.append(new Entry(3, 100, State.OPEN, "feat/e", "feat/d"));
        Model menu = Update.update(model(LOCAL, three), new StackMsg.MenuRequested(Option.none(), Option.of(12)))
                .model();

        Update.Next chosen = Update.update(menu, new StackMsg.PushChosen());

        assertEquals(List.empty(), chosen.cmds());
        assertEquals(
                Option.of("Stack #12 is not pushed: #100 is not checked out here"),
                TestModels.errorText(chosen.model()));
    }
}
