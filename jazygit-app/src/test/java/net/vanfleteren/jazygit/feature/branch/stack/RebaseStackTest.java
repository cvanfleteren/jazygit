package net.vanfleteren.jazygit.feature.branch.stack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.time.Instant;
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
import net.vanfleteren.jazygit.git.StubIntegrate;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.Fixtures;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
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
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
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
                List.of(new StackLayer(C, ORIGIN_MAIN), new StackLayer(D, new Revision.Commit(C_TIP))),
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

    @Test
    void aBranchBehindOriginIsPulledFirst() {
        Branch behind = Fixtures.branch(
                D,
                false,
                new Sha("dddd"),
                Instant.EPOCH,
                true,
                false,
                Option.of(new Upstream(new BranchRef.Remote(new RemoteBranch("origin", D)), 0, 2)));

        assertEquals(Option.of(StackRefusal.BEHIND_REMOTE), refusal(model(LOCAL.update(2, behind), ENTRIES), ENTRIES));
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
                new StackLayer(C, new Revision.Commit(new Sha("b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0"))),
                plan(model(LOCAL, merged), merged).get().layers().head());
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
                new StackLayer.Commits(C, List.of(new Sha("c1c1")), List.empty()),
                new StackLayer.Commits(
                        D, List.of(new Sha("d1d1")), List.of(new StackLayer.Merge(new Sha("eeee"), "Merge main"))));
        Model asking = Update.update(chosen.model(), new StackMsg.StackRead(rebase, commits))
                .model();

        StackPopups.RebaseConfirm popup =
                asking.popup(StackPopups.RebaseConfirm.class).get();
        assertEquals(new StackRebaseCmd.RebaseStack(rebase, commits), popup.cmd());
        assertEquals(
                "Puts feat/c, feat/d each on the one below, the bottom on origin/main, in one rebase.\n\n"
                        + "feat/d: its merge \"Merge main\" is dropped",
                StackDialogs.rebased(popup));
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

    @Test
    void theRebaseChecksOutTheBranchThatWasThenOffersToPush() {
        StackRebase rebase = plan(MODEL, ENTRIES).get();
        StackRebaseCmd.RebaseStack cmd = new StackRebaseCmd.RebaseStack(rebase, List.empty());

        Msg done = cmd.run(new Env(StubGit.of(), StubGitHub.empty()));

        assertInstanceOf(StackMsg.Rebased.class, done);
        Model offered = Update.update(TestModels.running(MODEL, cmd), done).model();
        assertEquals(
                Option.of(new StackPopups.PushOffer(new StackRebaseCmd.PushStack(12, List.of(C, D)))), offered.popup());
    }

    @Test
    void aRebaseThatStopsOnAConflictChecksNothingOut() {
        StackRebaseCmd.RebaseStack cmd =
                new StackRebaseCmd.RebaseStack(plan(MODEL, ENTRIES).get(), List.empty());
        Git stopping = StubGit.of().withIntegrate(new StubIntegrate() {
            @Override
            public Either<GitFailure<GitError>, List<ExecutedCommand>> rebaseStack(
                    Revision base, List<StackLayer.Commits> layers) {
                return Either.left(new GitFailure<>(
                        new GitError.StoppedOnConflicts(Option.none(), 1), List.empty(), Option.none()));
            }
        });

        assertInstanceOf(OperationMsg.Stopped.class, cmd.run(new Env(stopping, StubGitHub.empty())));
    }

    @Test
    void pushingTheStackPushesItsOpenBranchesAtOnce() {
        Model menu = Update.update(MODEL, new StackMsg.MenuRequested(Option.none(), Option.of(12)))
                .model();

        assertEquals(
                List.of(new StackRebaseCmd.PushStack(12, List.of(C, D))),
                Update.update(menu, new StackMsg.PushChosen()).cmds());
    }
}
