package net.vanfleteren.jazygit.feature.branch.stack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.time.Instant;
import net.vanfleteren.jazygit.feature.branch.checkout.CheckoutCmd;
import net.vanfleteren.jazygit.feature.branch.stack.StackCheckout.Kind;
import net.vanfleteren.jazygit.feature.branch.stack.StackCheckout.Landing;
import net.vanfleteren.jazygit.feature.branch.stack.StackCheckout.Status;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.State;
import net.vanfleteren.jazygit.gh.model.PullRequestStack;
import net.vanfleteren.jazygit.gh.model.PullRequestStack.Entry;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.git.model.AllBranches;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.CaughtUpBranch;
import net.vanfleteren.jazygit.git.model.Fixtures;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.RemoteName;
import net.vanfleteren.jazygit.git.model.RemoteRef;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.Tracking;
import net.vanfleteren.jazygit.git.model.Upstream;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.operation.Notice;
import net.vanfleteren.jazygit.state.prs.PrOverview;
import org.junit.jupiter.api.Test;

/**
 * Checking out a stack on GitHub, or updating its branches, from its menu (ADR 99).
 */
class CheckOutStackTest {

    private static final RemoteName ORIGIN = new RemoteName("origin");
    private static final BranchName MAIN = new BranchName("main");
    private static final BranchName A = new BranchName("feat/a");
    private static final BranchName B = new BranchName("feat/b");
    private static final BranchName C = new BranchName("feat/c");
    private static final Sha A_TIP = new Sha("a".repeat(40));
    private static final Sha A_HEAD = new Sha("1".repeat(40));

    // Stack #12 on main: #95 merged, then feat/a (#96), feat/b (#97) and feat/c (#98), all open.
    private static final List<Entry> ENTRIES = List.of(
            new Entry(1, 95, State.MERGED, "feat/old", "main"),
            new Entry(2, 96, State.OPEN, "feat/a", "main", Option.of("1".repeat(40)), false),
            new Entry(3, 97, State.OPEN, "feat/b", "feat/a", Option.of("b".repeat(40)), false),
            new Entry(4, 98, State.OPEN, "feat/c", "feat/b", Option.of("c".repeat(40)), false));

    private static PullRequestStack stack(List<Entry> entries, int position) {
        return new PullRequestStack(12, position, entries.size(), entries);
    }

    private static final PullRequestStack STACK = stack(ENTRIES, 2);

    private static RemoteBranch origin(BranchName branch) {
        return new RemoteBranch(ORIGIN, branch);
    }

    private static Branch following(BranchName name, Sha tip, int ahead, int behind) {
        return new Branch(
                name,
                false,
                tip,
                Instant.EPOCH,
                Option.none(),
                true,
                false,
                new Tracking.Tracks(new Upstream(new BranchRef.Remote(origin(name)), ahead, behind)),
                Option.none());
    }

    private static RemoteRef remote(BranchName branch, Sha tip) {
        return new RemoteRef(origin(branch), tip, Instant.EPOCH);
    }

    private static final Branch MAIN_HERE = Fixtures.branch(MAIN, true, new Sha("0000"), Instant.EPOCH, true, true);

    // What the branches are once fetched: feat/a one commit behind origin/feat/a, feat/b and feat/c only on origin.
    private static final AllBranches FETCHED = new AllBranches(
            List.of(MAIN_HERE, following(A, A_TIP, 0, 1)),
            List.of(remote(A, A_HEAD), remote(B, new Sha("b".repeat(40))), remote(C, new Sha("c".repeat(40)))));

    private static StackCheckout plan(AllBranches read, Option<Integer> on) {
        return StackCheckout.of(STACK, ORIGIN, read, on);
    }

    private static AllBranches withLocal(Branch branch) {
        return new AllBranches(FETCHED.local().append(branch), FETCHED.remote());
    }

    private static Kind kindOf(StackCheckout checkout, int number) {
        return checkout.lines().find(line -> line.number() == number).get().kind();
    }

    // main checked out, and feat/a of stack #12, whose pull request the model knows.
    private static Model model(List<Branch> local) {
        Model read = TestModels.localBranchesRead(TestModels.loaded(), local).model();
        return read.mapGitHub(gitHub -> gitHub.withPullRequests(Option.of(HashMap.ofEntries(local.filter(
                        branch -> !branch.name().equals(MAIN))
                .map(branch -> io.vavr.Tuple.of(
                        branch.name(),
                        new PullRequest(
                                96 + List.of(A, B, C).indexOf(branch.name()),
                                branch.name().value(),
                                "",
                                false,
                                Checks.NONE,
                                Review.NONE,
                                Option.of(stack(ENTRIES, 2 + List.of(A, B, C).indexOf(branch.name()))),
                                Option.none())))))));
    }

    private static final Model ONE_LOCAL = model(List.of(MAIN_HERE, following(A, A_TIP, 0, 0)));

    @Test
    void eachOpenPullRequestSaysWhatHappensToItsBranchTheMergedOneIsLeftOut() {
        StackCheckout checkout = plan(FETCHED, Option.none());

        assertEquals(List.of(96, 97, 98), checkout.lines().map(StackCheckout.Line::number));
        assertEquals(
                List.of(Kind.FAST_FORWARDED, Kind.MADE, Kind.MADE),
                checkout.lines().map(StackCheckout.Line::kind));
        assertEquals(1, checkout.lines().head().commits());
        assertEquals(List.of(95), checkout.skipped());
        assertTrue(checkout.startable());
    }

    @Test
    void theBranchesMadeFollowTheirBranchOnTheRemoteAndRecordTheBaseOfTheirPullRequest() {
        assertEquals(
                List.of(
                        new CaughtUpBranch(A, origin(A), false, Option.none()),
                        new CaughtUpBranch(B, origin(B), true, Option.of(A)),
                        new CaughtUpBranch(C, origin(C), true, Option.of(B))),
                plan(FETCHED, Option.none()).caughtUp());
    }

    @Test
    void aBranchWithCommitsOfItsOwnIsKeptAsItIs() {
        StackCheckout checkout =
                plan(new AllBranches(List.of(MAIN_HERE, following(A, A_TIP, 2, 1)), FETCHED.remote()), Option.none());

        assertEquals(Kind.OWN_COMMITS, kindOf(checkout, 96));
        assertEquals(2, checkout.lines().head().commits());
        assertFalse(checkout.caughtUp().exists(branch -> branch.branch().equals(A)));
        assertTrue(checkout.startable());
    }

    @Test
    void aBranchBehindThatIsCheckedOutHereOrElsewhereIsKeptAsItIs() {
        Branch here = new Branch(
                A,
                true,
                A_TIP,
                Instant.EPOCH,
                Option.none(),
                true,
                false,
                following(A, A_TIP, 0, 1).tracking(),
                Option.none());
        Branch elsewhere = new Branch(
                A,
                false,
                A_TIP,
                Instant.EPOCH,
                Option.none(),
                true,
                false,
                following(A, A_TIP, 0, 1).tracking(),
                Option.of(Path.of("/elsewhere")));

        assertEquals(
                Kind.CHECKED_OUT_HERE,
                kindOf(plan(new AllBranches(List.of(here), FETCHED.remote()), Option.none()), 96));
        assertEquals(
                Kind.OTHER_WORKTREE,
                kindOf(plan(new AllBranches(List.of(elsewhere), FETCHED.remote()), Option.none()), 96));
    }

    @Test
    void aBranchOfTheSameNameThatFollowsNothingOrAnotherBranchKeepsItFromStarting() {
        Branch unfollowing = Fixtures.branch(B, false, new Sha("9999"));
        Branch other = new Branch(
                B,
                false,
                new Sha("9999"),
                Instant.EPOCH,
                Option.none(),
                true,
                false,
                new Tracking.Tracks(new Upstream(new BranchRef.Remote(origin(new BranchName("elsewhere"))), 0, 0)),
                Option.none());

        StackCheckout none = plan(withLocal(unfollowing), Option.none());
        StackCheckout another = plan(withLocal(other), Option.none());

        assertEquals(Kind.NO_UPSTREAM, kindOf(none, 97));
        assertEquals(Kind.OTHER_UPSTREAM, kindOf(another, 97));
        assertFalse(none.startable());
        assertFalse(another.startable());
    }

    @Test
    void aBranchFollowingItsBranchOnTheRemoteUnderAnotherNameIsThatOne() {
        Branch mine = new Branch(
                new BranchName("my-b"),
                false,
                new Sha("bbb0"),
                Instant.EPOCH,
                Option.none(),
                true,
                false,
                new Tracking.Tracks(new Upstream(new BranchRef.Remote(origin(B)), 0, 2)),
                Option.none());

        StackCheckout checkout = plan(withLocal(mine), Option.none());

        assertEquals(new BranchName("my-b"), checkout.lines().get(1).branch());
        assertEquals(Kind.FAST_FORWARDED, kindOf(checkout, 97));
    }

    @Test
    void aBranchInAForkIsLeftOutAndNotFetched() {
        List<Entry> forked = ENTRIES.update(
                3, new Entry(4, 98, State.OPEN, "feat/c", "feat/b", Option.of("c".repeat(40)), false, true));

        assertEquals(List.of(A, B), StackCheckout.fetched(stack(forked, 2)));
        StackCheckout checkout = StackCheckout.of(stack(forked, 2), ORIGIN, FETCHED, Option.none());
        assertEquals(Kind.FORK, kindOf(checkout, 98));
        // The top is the highest pull request with a branch here.
        assertEquals(Option.of(B), checkout.top());
    }

    @Test
    void enterLandsOnThePullRequestItWasAskedForOnElseOnTheTop() {
        StackCheckout asked = plan(FETCHED, Option.of(97));
        StackCheckout header = plan(FETCHED, Option.none());

        assertEquals(Landing.ASKED, asked.preferred());
        assertEquals(Option.of(B), asked.target(Landing.ASKED));
        assertEquals(Landing.TOP, header.preferred());
        assertEquals(Option.of(C), header.target(Landing.TOP));
        assertEquals(Option.none(), header.target(Landing.STAY));
    }

    @Test
    void theMenuSaysWhetherTheStackIsCheckedOutUpdatedOrUpToDateWithoutReadingAnything() {
        Branch atHead = following(A, A_HEAD, 0, 0);
        List<Branch> all = List.of(
                MAIN_HERE,
                atHead,
                following(B, new Sha("b".repeat(40)), 0, 0),
                following(C, new Sha("c".repeat(40)), 0, 0));

        assertEquals(Status.CHECK_OUT, StackCheckout.status(ONE_LOCAL, STACK));
        assertEquals(Status.UP_TO_DATE, StackCheckout.status(model(all), STACK));
        assertEquals(Status.UPDATE, StackCheckout.status(model(all.update(1, following(A, A_TIP, 0, 0))), STACK));
        // A branch whose commit on GitHub is not known may be behind.
        List<Entry> unknown = ENTRIES.update(1, new Entry(2, 96, State.OPEN, "feat/a", "main"));
        assertEquals(Status.UPDATE, StackCheckout.status(model(all), stack(unknown, 2)));
        assertEquals(
                Status.NOTHING,
                StackCheckout.status(ONE_LOCAL, stack(List.of(new Entry(1, 95, State.MERGED, "x", "main")), 1)));
    }

    @Test
    void anUpToDateStackShowsItsCheckoutDisabledInTheMenu() {
        StackPopups.Menu upToDate = new StackPopups.Menu(
                Option.none(),
                Option.none(),
                Option.of(STACK),
                Option.none(),
                false,
                List.empty(),
                Option.none(),
                Status.UP_TO_DATE);
        StackPopups.Menu missing = new StackPopups.Menu(
                Option.none(),
                Option.none(),
                Option.of(STACK),
                Option.none(),
                false,
                List.empty(),
                Option.none(),
                Status.CHECK_OUT);

        assertFalse(upToDate.canCheckOut());
        assertTrue(missing.canCheckOut());
        assertEquals("update the stack", StackDialogs.checkoutLabel(upToDate));
        assertEquals("check out the stack", StackDialogs.checkoutLabel(missing));
    }

    @Test
    void choosingToCheckOutTheStackFetchesItsBranchesFromTheRemoteTheOthersFollow() {
        Model menu = Update.update(ONE_LOCAL, new StackMsg.MenuRequested(Option.of(A), Option.none()))
                .model();
        assertEquals(
                Option.of(Status.CHECK_OUT), menu.popup(StackPopups.Menu.class).map(StackPopups.Menu::checkout));

        Update.Next chosen = Update.update(menu, new StackMsg.CheckoutChosen());

        assertEquals(List.of(new StackCheckoutCmd.Fetch(STACK, ORIGIN, Option.of(96))), chosen.cmds());
        assertTrue(chosen.model().popup(StackPopups.Menu.class).isEmpty());
    }

    @Test
    void onceFetchedItAsksWhatHappens() {
        StackCheckout checkout = plan(FETCHED, Option.of(96));
        StackCheckoutCmd.Fetch fetch = new StackCheckoutCmd.Fetch(STACK, ORIGIN, Option.of(96));

        Model asked = Update.update(ONE_LOCAL, new StackMsg.StackFetched(fetch, List.empty(), checkout))
                .model();

        assertEquals(Option.of(new StackPopups.Checkout(checkout)), asked.popup(StackPopups.Checkout.class));
        assertTrue(StackDialogs.checkedOut(checkout).contains("new, following origin/feat/b"));
    }

    @Test
    void confirmingMakesTheBranchesThenChecksOutTheOneChosen() {
        StackCheckout checkout = plan(FETCHED, Option.none());
        Model asked = ONE_LOCAL.openPopup(new StackPopups.Checkout(checkout));

        Update.Next confirmed = Update.update(asked, new StackMsg.CheckoutConfirmed(Landing.TOP));
        StackCheckoutCmd.CatchUp catchUp = new StackCheckoutCmd.CatchUp(checkout, Landing.TOP);
        assertEquals(List.of(catchUp), confirmed.cmds());

        Update.Next done =
                Update.update(confirmed.model(), new StackMsg.CaughtUp(catchUp, List.<ExecutedCommand>empty()));

        assertEquals(
                Option.of(new Notice.StackCheckedOut(12, 2, 1, 0)),
                done.model().outcome().notice());
        assertTrue(done.cmds().contains(new CheckoutCmd.Checkout(C)));
    }

    @Test
    void stayingChecksOutNothing() {
        StackCheckout checkout = plan(FETCHED, Option.none());
        StackCheckoutCmd.CatchUp catchUp = new StackCheckoutCmd.CatchUp(checkout, Landing.STAY);

        Update.Next done = Update.update(ONE_LOCAL, new StackMsg.CaughtUp(catchUp, List.empty()));

        assertFalse(done.cmds().exists(CheckoutCmd.Checkout.class::isInstance));
    }

    @Test
    void aStackThatCannotStartIsNotCheckedOut() {
        StackCheckout refused = plan(withLocal(Fixtures.branch(B, false, new Sha("9999"))), Option.none());
        Model asked = ONE_LOCAL.openPopup(new StackPopups.Checkout(refused));

        Update.Next confirmed = Update.update(asked, new StackMsg.CheckoutConfirmed(Landing.TOP));

        assertEquals(List.empty(), confirmed.cmds());
        assertTrue(StackDialogs.checkedOut(refused).contains("rename or delete it first"));
    }

    private static PullRequestSummary summary(String repository, Option<PullRequestStack> stack) {
        return new PullRequestSummary(
                repository,
                97,
                "Refresh flow",
                "sam",
                "feat/b",
                false,
                false,
                Checks.NONE,
                Review.NONE,
                Instant.EPOCH,
                "https://github.com/" + repository + "/pull/97",
                stack);
    }

    private static Model ofOwnerRepo(Model model) {
        return model.withPrOverview(PrOverview.INITIAL.withRepositoryName(Option.of("owner/repo")));
    }

    @Test
    void onThePullRequestsScreenTheMenuOpensForAStackedPullRequestOfThisRepository() {
        PullRequestStack stack = stack(ENTRIES, 3);

        Model menu = Update.update(
                        ofOwnerRepo(ONE_LOCAL),
                        new StackMsg.PullRequestMenuRequested(summary("owner/repo", Option.of(stack))))
                .model();

        StackPopups.Menu opened = menu.popup(StackPopups.Menu.class).get();
        assertEquals(Option.of(stack), opened.stack());
        assertEquals(Option.of(97), opened.on());
        assertTrue(opened.canCheckOut());
    }

    @Test
    void onThePullRequestsScreenAPullRequestOfAnotherRepositoryOrOutsideAStackOpensNoMenu() {
        Model other = Update.update(
                        ofOwnerRepo(ONE_LOCAL),
                        new StackMsg.PullRequestMenuRequested(summary("other/repo", Option.of(STACK))))
                .model();
        Model unstacked = Update.update(
                        ofOwnerRepo(ONE_LOCAL),
                        new StackMsg.PullRequestMenuRequested(summary("owner/repo", Option.none())))
                .model();

        assertTrue(other.popup(StackPopups.Menu.class).isEmpty());
        assertInstanceOf(AppError.OtherRepository.class, other.outcome().error().get());
        assertTrue(unstacked.popup().isEmpty());
    }
}
