package net.vanfleteren.jazygit.features.github.panes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.gh.model.GhBranch;
import net.vanfleteren.jazygit.core.gh.model.PrNumber;
import net.vanfleteren.jazygit.core.gh.model.PullRequest;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.core.gh.model.PullRequestDetails.State;
import net.vanfleteren.jazygit.core.gh.model.PullRequestStack;
import net.vanfleteren.jazygit.core.git.model.BaseDistance;
import net.vanfleteren.jazygit.core.git.model.Fixtures;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.github.stack.StackAction;
import net.vanfleteren.jazygit.features.github.state.GitHubState;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.ui.branches.BranchGroups.Entry;
import net.vanfleteren.jazygit.kernel.ui.branches.BranchGroups.Group;
import net.vanfleteren.jazygit.kernel.ui.widgets.LoadableList;
import org.junit.jupiter.api.Test;

/**
 * The stacks of pull requests on GitHub as groups of the branches pane (ADR 72, ADR 167): their pull requests in
 * GitHub's order, the bottom one first; those not checked out on dimmed lines; the header with the number, the base and
 * the size of the stack, and whether it diverged (ADR 73).
 */
class StackGroupsTest {

    private static final List<PullRequestStack.Entry> ENTRIES = List.of(
            new PullRequestStack.Entry(1, new PrNumber(96), State.OPEN, new GhBranch("feat/a"), new GhBranch("main")),
            new PullRequestStack.Entry(2, new PrNumber(97), State.OPEN, new GhBranch("feat/b"), new GhBranch("feat/a")),
            new PullRequestStack.Entry(
                    3, new PrNumber(98), State.OPEN, new GhBranch("feat/c"), new GhBranch("feat/b")));

    private static Branch branch(String name) {
        return Fixtures.branch(new BranchName(name), false, new Sha("aaaa"));
    }

    private static PullRequest stacked(int number, String head, int position, List<PullRequestStack.Entry> entries) {
        return new PullRequest(
                new PrNumber(number),
                new GhBranch(head),
                Option.none(),
                false,
                Checks.NONE,
                Review.NONE,
                Option.of(new PullRequestStack(12, position, entries.size(), entries)),
                Option.none());
    }

    private static Model withPullRequests(Model model, Map<String, PullRequest> byBranch) {
        return model.mapPart(
                GitHubState.PART, gitHub -> gitHub.withPullRequests(Option.of(byBranch.mapKeys(BranchName::new))));
    }

    private static List<String> entries(Group group) {
        return group.entries().map(entry -> switch (entry) {
            case Entry.Member member -> member.branch().value();
            case Entry.Elsewhere elsewhere -> elsewhere.key() + " " + elsewhere.text();
        });
    }

    private static String text(List<LoadableList.Seg> segs) {
        return segs.map(LoadableList.Seg::text).mkString();
    }

    @Test
    void aStackIsAGroupOfItsPullRequestsBottomFirst() {
        List<Branch> branches = List.of(branch("feat/c"), branch("feat/x"), branch("feat/a"), branch("feat/b"));
        Model model = withPullRequests(
                TestModels.loaded(),
                HashMap.of(
                        "feat/a", stacked(96, "feat/a", 1, ENTRIES),
                        "feat/b", stacked(97, "feat/b", 2, ENTRIES),
                        "feat/c", stacked(98, "feat/c", 3, ENTRIES)));

        List<Group> groups = StackGroups.of(model, branches);

        assertEquals(List.of("stack:12"), groups.map(Group::key));
        assertEquals(List.of("feat/a", "feat/b", "feat/c"), entries(groups.head()));
    }

    @Test
    void aPullRequestOfTheStackWhoseBranchIsNotCheckedOutIsADimmedLineThatNamesWhyItHasNoLog() {
        List<PullRequestStack.Entry> entries = List.of(
                new PullRequestStack.Entry(
                        1, new PrNumber(95), State.MERGED, new GhBranch("feat/0"), new GhBranch("main")),
                new PullRequestStack.Entry(
                        2, new PrNumber(96), State.OPEN, new GhBranch("feat/a"), new GhBranch("feat/0")),
                new PullRequestStack.Entry(
                        3, new PrNumber(97), State.OPEN, new GhBranch("feat/b"), new GhBranch("feat/a")));
        Model model = withPullRequests(TestModels.loaded(), HashMap.of("feat/b", stacked(97, "feat/b", 3, entries)));

        Group group = StackGroups.of(model, List.of(branch("feat/b"))).head();

        assertEquals(
                List.of("pullRequest:95 #95 ◆ merged", "pullRequest:96 #96 not checked out", "feat/b"), entries(group));
        assertEquals(
                List.of("#95", "#96"),
                group.entries()
                        .filter(Entry.Elsewhere.class::isInstance)
                        .map(entry -> ((Entry.Elsewhere) entry).unknown()));
    }

    @Test
    void theReadThatListsTheMostPullRequestsOfTheStackSaysWhatItHas() {
        // feat/a was read with the stack as it was before feat/c joined it; it is listed first all the same.
        Model model = withPullRequests(
                TestModels.loaded(),
                HashMap.of(
                        "feat/a", stacked(96, "feat/a", 1, ENTRIES.take(2)),
                        "feat/b", stacked(97, "feat/b", 2, ENTRIES)));

        Group group = StackGroups.of(model, List.of(branch("feat/a"), branch("feat/b")))
                .head();

        assertEquals(" 3 PRs", text(group.after()));
        assertEquals(List.of("feat/a", "feat/b", "pullRequest:98 #98 not checked out"), entries(group));
    }

    @Test
    void aBranchTheStackItIsShownWithDoesNotListComesAfterTheListedOnesByItsPosition() {
        Model model = withPullRequests(
                TestModels.loaded(),
                HashMap.of(
                        "feat/a", stacked(96, "feat/a", 1, ENTRIES),
                        "feat/b", stacked(97, "feat/b", 2, ENTRIES),
                        "feat/c", stacked(98, "feat/c", 3, ENTRIES),
                        "feat/z", stacked(100, "feat/z", 5, List.of(entry(5, 100, "feat/z", "feat/y"))),
                        "feat/y", stacked(99, "feat/y", 4, List.of(entry(4, 99, "feat/y", "feat/c")))));

        Group group = StackGroups.of(
                        model,
                        List.of(
                                branch("feat/z"),
                                branch("feat/y"),
                                branch("feat/a"),
                                branch("feat/b"),
                                branch("feat/c")))
                .head();

        assertEquals(List.of("feat/a", "feat/b", "feat/c", "feat/y", "feat/z"), entries(group));
    }

    @Test
    void aClosedPullRequestOfTheStackSaysItIsClosed() {
        List<PullRequestStack.Entry> entries = List.of(
                new PullRequestStack.Entry(
                        1, new PrNumber(95), State.CLOSED, new GhBranch("feat/0"), new GhBranch("main")),
                new PullRequestStack.Entry(
                        2, new PrNumber(96), State.OPEN, new GhBranch("feat/a"), new GhBranch("feat/0")));
        Model model = withPullRequests(TestModels.loaded(), HashMap.of("feat/a", stacked(96, "feat/a", 2, entries)));

        Group group = StackGroups.of(model, List.of(branch("feat/a"))).head();

        assertEquals(List.of("pullRequest:95 #95 closed", "feat/a"), entries(group));
    }

    private static PullRequestStack.Entry entry(int position, int number, String head, String base) {
        return new PullRequestStack.Entry(
                position, new PrNumber(number), State.OPEN, new GhBranch(head), new GhBranch(base));
    }

    @Test
    void theHeaderNamesTheStackItsBaseAndHowManyPullRequestsItHas() {
        Model model = withPullRequests(
                TestModels.loaded(),
                HashMap.of("feat/a", stacked(96, "feat/a", 1, ENTRIES), "feat/b", stacked(97, "feat/b", 2, ENTRIES)));

        Group group = StackGroups.of(model, List.of(branch("feat/a"), branch("feat/b")))
                .head();

        assertEquals("stack #12 → main", text(group.title()));
        assertEquals(" 3 PRs", text(group.after()));
        assertEquals(StackAction.STACKS, group.keys().scope());
        assertEquals(12, group.keys().subject());
    }

    @Test
    void aStackWithABranchBehindTheOneBelowSaysItDiverged() {
        BranchName main = new BranchName("main");
        BranchName featA = new BranchName("feat/a");
        BranchName featB = new BranchName("feat/b");
        Model read = TestModels.localBranchesRead(
                        TestModels.loaded(), List.of(main, featA, featB).map(name -> branch(name.value())))
                .model();
        Model stacked = withPullRequests(
                read.mapRepo(repo -> repo.withParents(HashMap.of(featA, main, featB, featA))),
                HashMap.of("feat/a", stacked(96, "feat/a", 1, ENTRIES), "feat/b", stacked(97, "feat/b", 2, ENTRIES)));
        List<Branch> members = List.of(branch("feat/a"), branch("feat/b"));

        Model diverged = stacked.mapRepo(repo -> repo.withBaseDistance(HashMap.of(featB, behind(featA, 2))));
        Model behindMain = stacked.mapRepo(repo -> repo.withBaseDistance(HashMap.of(featA, behind(main, 5))));

        assertEquals(
                " 3 PRs diverged: rebase it",
                text(StackGroups.of(diverged, members).head().after()));
        assertEquals(" 3 PRs", text(StackGroups.of(behindMain, members).head().after()));
    }

    private static BaseDistance behind(BranchName base, int commits) {
        return new BaseDistance(new BranchRef.Remote(new RemoteBranch("origin", base)), new Sha("bbbb"), 1, commits);
    }
}
