package net.vanfleteren.jazygit.feature.branch.stack;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.stack.StackChoice.BaseChange;
import net.vanfleteren.jazygit.feature.branch.stack.StackChoice.Plan;
import net.vanfleteren.jazygit.feature.branch.stack.StackChoice.Refusal;
import net.vanfleteren.jazygit.gh.model.GhBranch;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.State;
import net.vanfleteren.jazygit.gh.model.PullRequestStack;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.Fixtures;
import net.vanfleteren.jazygit.git.model.Sha;
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
                number,
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
                        List.of(new BaseChange(97, Option.of(new GhBranch("develop")), new GhBranch("feat/a"))))),
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
                12,
                1,
                1,
                List.of(new PullRequestStack.Entry(1, 98, State.OPEN, new GhBranch("feat/c"), new GhBranch("feat/b"))));
        PullRequest stacked = new PullRequest(
                98,
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

    @Test
    void aLoopOfParentsEnds() {
        Map<BranchName, BranchName> loop = HashMap.of(name("feat/a"), name("feat/b"), name("feat/b"), name("feat/a"));

        StackChoice choice = StackChoice.of(B, List.of(A, B), loop, n -> false, b -> Option.none());

        assertEquals(List.of(A, B), choice.rows().map(StackChoice.Row::branch));
    }
}
