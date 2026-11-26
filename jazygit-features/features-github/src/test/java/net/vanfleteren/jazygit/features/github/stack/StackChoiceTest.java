package net.vanfleteren.jazygit.features.github.stack;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.gh.model.GhBranch;
import net.vanfleteren.jazygit.core.gh.model.PullRequest;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.core.gh.model.PullRequestDetails.State;
import net.vanfleteren.jazygit.core.gh.model.PullRequestStack;
import net.vanfleteren.jazygit.core.gh.model.StackId;
import net.vanfleteren.jazygit.core.git.model.Fixtures;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.PrNumber;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.github.stack.StackChoice.BaseChange;
import net.vanfleteren.jazygit.features.github.stack.StackChoice.Plan;
import net.vanfleteren.jazygit.features.github.stack.StackChoice.Refusal;
import org.junit.jupiter.api.Test;

/**
 * The branches a stack is made of from one branch, and the bottom and the top chosen among them (ADR 73).
 */
class StackChoiceTest {

    private static BranchName name(String name) {
        return new BranchName(name);
    }

    private static Branch branch(String name) {
        return Fixtures.branch(name(name), false, new Sha("aaaa"));
    }

    private static final Branch DEVELOP = branch("develop");
    private static final Branch A = branch("feat/a");
    private static final Branch B = branch("feat/b");
    private static final Branch C = branch("feat/c");
    private static final Branch D = branch("feat/d");
    private static final Branch E = branch("feat/e");
    private static final List<Branch> LOCAL = List.of(DEVELOP, A, B, C, D, E);

    // develop ← feat/a ← feat/b ← feat/c ← feat/d, and feat/b ← feat/e: the example of ADR 73.
    private static final Map<BranchName, BranchName> PARENTS = HashMap.of(
            name("feat/a"), name("develop"),
            name("feat/b"), name("feat/a"),
            name("feat/c"), name("feat/b"),
            name("feat/d"), name("feat/c"),
            name("feat/e"), name("feat/b"));

    private static PullRequest pr(int number, String head, String base) {
        return new PullRequest(
                new PrNumber(number),
                new GhBranch(head),
                Option.of(new GhBranch(base)),
                false,
                Checks.NONE,
                Review.NONE,
                Option.none(),
                Option.none());
    }

    // feat/b's pull request is still on develop, as one opened from the parent gh-merge-base recorded.
    private static final Map<BranchName, PullRequest> OPEN = HashMap.of(
            name("feat/a"), pr(96, "feat/a", "develop"),
            name("feat/b"), pr(97, "feat/b", "develop"),
            name("feat/c"), pr(98, "feat/c", "feat/b"),
            name("feat/e"), pr(99, "feat/e", "feat/b"));

    private static StackChoice choice(Branch branch, Map<BranchName, PullRequest> open) {
        return StackChoice.of(branch, LOCAL, PARENTS, n -> n.equals(name("develop")), b -> open.get(b.name()));
    }

    private static StackChoice choice() {
        return choice(B, OPEN);
    }

    @Test
    void theLineBelowComesFirstThenTheBranchThenTheTreeAboveItDepthFirst() {
        StackChoice choice = choice();

        assertEquals(List.of(A, B, C, D, E), choice.rows().map(StackChoice.Row::branch));
        assertEquals(List.of(0, 0, 1, 2, 1), choice.rows().map(StackChoice.Row::depth));
        assertEquals(1, choice.branch());
        assertEquals(Option.of(name("develop")), choice.base());
    }

    @Test
    void theBottomStartsAsFarDownAsThereArePullRequestsAndTheTopAtTheBranch() {
        StackChoice choice = choice();

        assertEquals(0, choice.bottom());
        assertEquals(1, choice.top());
    }

    @Test
    void theBottomStopsAboveABranchWithoutAPullRequest() {
        assertEquals(1, choice(B, OPEN.remove(name("feat/a"))).bottom());
    }

    @Test
    void basesThatDoNotMakeOneLineAreChangedBeforeTheStackIsMade() {
        assertEquals(
                Either.right(new Plan(
                        List.of(
                                OPEN.get(name("feat/a")).get(),
                                OPEN.get(name("feat/b")).get()),
                        List.of(new BaseChange(
                                new PrNumber(97), Option.of(new GhBranch("develop")), new GhBranch("feat/a"))))),
                choice().plan(2, 0));
    }

    @Test
    void theOneLineFromTheBottomToTheTopLeavesOutTheOtherForks() {
        StackChoice choice = choice().withTop(4).get();

        assertEquals(List.of(A, B, E), choice.chosen().map(StackChoice.Row::branch));
        assertEquals(List.of(true, true, false, false, true), List.range(0, 5).map(choice::isChosen));
    }

    @Test
    void aBranchWithoutAPullRequestBetweenTheBottomAndTheTopMakesNoStack() {
        assertEquals(
                Either.left(new Refusal.NoPullRequest(List.of(name("feat/d")))),
                choice().withTop(3).get().plan(2, 0));
    }

    @Test
    void aBottomAboveTheBranchOrATopBelowItIsRefused() {
        assertEquals(Option.none(), choice().withBottom(2));
        assertEquals(Option.none(), choice().withTop(0));
    }

    @Test
    void onePullRequestMakesNoStack() {
        assertEquals(
                Either.left(new Refusal.TooFew()), choice().withBottom(1).get().plan(2, 0));
    }

    @Test
    void aPullRequestInAStackAlreadyIsNotStackedAgain() {
        PullRequestStack stack = new PullRequestStack(
                new StackId(12),
                1,
                1,
                List.of(new PullRequestStack.Entry(
                        1, new PrNumber(98), State.OPEN, new GhBranch("feat/c"), new GhBranch("feat/b"))));
        PullRequest stacked = new PullRequest(
                new PrNumber(98),
                new GhBranch("feat/c"),
                Option.of(new GhBranch("feat/b")),
                false,
                Checks.NONE,
                Review.NONE,
                Option.of(stack),
                Option.none());

        assertEquals(
                Either.left(new Refusal.Stacked(List.of(name("feat/c")))),
                choice(B, OPEN.put(name("feat/c"), stacked)).withTop(2).get().plan(2, 0));
    }

    @Test
    void addingPutsOnlyTheBranchesAboveTheTopOfTheStack() {
        StackChoice choice = StackChoice.above(B, LOCAL, PARENTS, b -> OPEN.get(b.name()));

        assertEquals(List.of(B, C, D, E), choice.rows().map(StackChoice.Row::branch));
        assertEquals(Either.left(new Refusal.TooFew()), choice.plan(1, 1));
        assertEquals(
                Either.right(new Plan(List.of(OPEN.get(name("feat/e")).get()), List.empty())),
                choice.withTop(3).get().plan(1, 1));
    }

    // ADR 148: one line above the top, with pull requests up to feat/d, which has none.
    @Test
    void addingStartsWithTheTopAsFarUpAsOneLineOfPullRequestsGoes() {
        StackChoice choice = StackChoice.above(A, LOCAL, PARENTS.remove(name("feat/e")), b -> OPEN.get(b.name()));

        assertEquals(List.of(A, B, C, D), choice.rows().map(StackChoice.Row::branch));
        assertEquals(2, choice.top());
        assertEquals(Option.none(), choice.toCreate());
    }

    @Test
    void addingStartsWithTheFirstBranchAboveWhenItHasNoPullRequestToCreateIt() {
        StackChoice choice = StackChoice.above(C, LOCAL, PARENTS, b -> OPEN.get(b.name()));

        assertEquals(1, choice.top());
        assertEquals(Option.of(D), choice.toCreate().map(StackChoice.Row::branch));
    }

    // feat/d has no pull request, feat/f above it has: with feat/f the top, feat/d is refused, not created.
    @Test
    void aBranchWithoutAPullRequestIsCreatedOnlyWhenItIsTheOneAdded() {
        Branch f = branch("feat/f");
        StackChoice choice = StackChoice.above(
                        C,
                        List.of(C, D, f),
                        HashMap.of(name("feat/d"), name("feat/c"), name("feat/f"), name("feat/d")),
                        b -> OPEN.put(name("feat/f"), pr(100, "feat/f", "feat/d"))
                                .get(b.name()))
                .withTop(2)
                .get();

        assertEquals(Option.none(), choice.toCreate());
        assertEquals(Either.left(new Refusal.NoPullRequest(List.of(name("feat/d")))), choice.plan(1, 1));
    }

    @Test
    void addingWhereTheBranchesAboveForkStartsWithTheTopOfTheStack() {
        assertEquals(
                0, StackChoice.above(B, LOCAL, PARENTS, b -> OPEN.get(b.name())).top());
    }

    // top ← mid ← high, found by history: each on the nearest it has; other is not eligible, as one with a parent.
    @Test
    void branchesFoundByHistoryAreEachPlacedOnTheNearestTheyHave() {
        assertEquals(
                HashMap.of(name("mid"), name("top"), name("high"), name("mid")),
                StackChoice.byHistory(
                        name("top"),
                        HashSet.of(name("mid"), name("high")),
                        HashMap.of(
                                name("top"), List.of(name("high"), name("mid"), name("other"), name("top")),
                                name("mid"), List.of(name("high"), name("mid")),
                                name("high"), List.of(name("high")))));
    }

    // c is listed above the top by its parent; x, made on c with plain git, is placed on c, not on the top.
    @Test
    void aBranchFoundByHistoryIsPlacedOnABranchListedByItsParent() {
        assertEquals(
                HashMap.of(name("x"), name("c")),
                StackChoice.byHistory(
                        name("top"),
                        HashSet.of(name("x")),
                        HashMap.of(
                                name("top"), List.of(name("c"), name("top"), name("x")),
                                name("c"), List.of(name("c"), name("x")),
                                name("x"), List.of(name("x")))));
    }

    // copy is at mid's commit: neither is built on the other.
    @Test
    void branchesAtTheSameCommitAreNotBuiltOnEachOther() {
        assertEquals(
                HashMap.of(name("mid"), name("top"), name("copy"), name("top")),
                StackChoice.byHistory(
                        name("top"),
                        HashSet.of(name("mid"), name("copy")),
                        HashMap.of(
                                name("top"), List.of(name("copy"), name("mid"), name("top")),
                                name("mid"), List.of(name("copy"), name("mid")),
                                name("copy"), List.of(name("copy"), name("mid")))));
    }

    @Test
    void aLoopOfParentsEnds() {
        Map<BranchName, BranchName> loop = HashMap.of(name("feat/a"), name("feat/b"), name("feat/b"), name("feat/a"));

        StackChoice choice = StackChoice.of(B, List.of(A, B), loop, n -> false, b -> Option.none());

        assertEquals(List.of(A, B), choice.rows().map(StackChoice.Row::branch));
    }
}
