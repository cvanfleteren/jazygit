package net.vanfleteren.jazygit.ui.branches;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.GhBranch;
import net.vanfleteren.jazygit.gh.model.PrNumber;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.State;
import net.vanfleteren.jazygit.gh.model.PullRequestStack;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.Fixtures;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.ui.widgets.LoadableList;
import org.junit.jupiter.api.Test;

/**
 * The nodes of the local branches: a stack on GitHub with a local branch is a header with its pull requests under
 * it (ADR 72).
 */
class BranchNodeTest {

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

    private static List<String> keys(List<Branch> branches, Map<String, PullRequest> byBranch, Set<Integer> closed) {
        return BranchNode.of(
                        branches, branch -> byBranch.get(branch.name().value()), true, closed, head -> Option.none())
                .map(BranchNode::key);
    }

    @Test
    void aStackIsAHeaderWithItsPullRequestsUnderItBottomFirst() {
        List<Branch> branches = List.of(branch("feat/c"), branch("feat/x"), branch("feat/a"), branch("feat/b"));
        Map<String, PullRequest> prs = HashMap.of(
                "feat/a", stacked(96, "feat/a", 1, ENTRIES),
                "feat/b", stacked(97, "feat/b", 2, ENTRIES),
                "feat/c", stacked(98, "feat/c", 3, ENTRIES));

        assertEquals(
                List.of("stack:12", "branch:feat/a", "branch:feat/b", "branch:feat/c", "branch:feat/x"),
                keys(branches, prs, HashSet.empty()));
    }

    @Test
    void aStackSitsWhereItsMostRecentBranchDoes() {
        List<Branch> branches = List.of(branch("feat/x"), branch("feat/b"), branch("main"), branch("feat/a"));
        Map<String, PullRequest> prs = HashMap.of(
                "feat/a", stacked(96, "feat/a", 1, ENTRIES.take(2)),
                "feat/b", stacked(97, "feat/b", 2, ENTRIES.take(2)));

        assertEquals(
                List.of("branch:feat/x", "stack:12", "branch:feat/a", "branch:feat/b", "branch:main"),
                keys(branches, prs, HashSet.empty()));
    }

    @Test
    void aPullRequestOfTheStackWhoseBranchIsNotCheckedOutStillShows() {
        List<PullRequestStack.Entry> entries = List.of(
                new PullRequestStack.Entry(
                        1, new PrNumber(95), State.MERGED, new GhBranch("feat/0"), new GhBranch("main")),
                new PullRequestStack.Entry(
                        2, new PrNumber(96), State.OPEN, new GhBranch("feat/a"), new GhBranch("feat/0")),
                new PullRequestStack.Entry(
                        3, new PrNumber(97), State.OPEN, new GhBranch("feat/b"), new GhBranch("feat/a")));

        List<BranchNode> nodes = BranchNode.of(
                List.of(branch("feat/b")),
                branch -> Option.of(stacked(97, "feat/b", 3, entries)),
                true,
                HashSet.empty(),
                head -> Option.none());

        assertEquals(
                List.of("stack:12", "pullRequest:95", "pullRequest:96", "branch:feat/b"), nodes.map(BranchNode::key));
        assertEquals(
                List.of("    #95 ◆ merged", "    #96 not checked out"),
                nodes.drop(1)
                        .take(2)
                        .map(node ->
                                LocalBranchesList.other((BranchNode.Other) node).text()));
    }

    @Test
    void unstackedEveryBranchIsANodeOfItsOwnInItsOrder() {
        List<Branch> branches = List.of(branch("feat/c"), branch("feat/x"), branch("feat/a"), branch("feat/b"));
        Map<String, PullRequest> prs = HashMap.of(
                "feat/a", stacked(96, "feat/a", 1, ENTRIES),
                "feat/b", stacked(97, "feat/b", 2, ENTRIES),
                "feat/c", stacked(98, "feat/c", 3, ENTRIES));

        List<BranchNode> nodes = BranchNode.of(
                branches, branch -> prs.get(branch.name().value()), false, HashSet.of(12), head -> Option.none());

        assertEquals(
                List.of("branch:feat/c", "branch:feat/x", "branch:feat/a", "branch:feat/b"),
                nodes.map(BranchNode::key));
        assertEquals(List.of(false, false, false, false), nodes.map(node -> ((BranchNode.Leaf) node).inStack()));
    }

    @Test
    void aClosedStackIsItsHeaderAlone() {
        List<Branch> branches = List.of(branch("feat/a"), branch("feat/b"), branch("feat/x"));
        Map<String, PullRequest> prs = HashMap.of(
                "feat/a", stacked(96, "feat/a", 1, ENTRIES.take(2)),
                "feat/b", stacked(97, "feat/b", 2, ENTRIES.take(2)));

        assertEquals(List.of("stack:12", "branch:feat/x"), keys(branches, prs, HashSet.of(12)));
    }

    @Test
    void theHeaderNamesTheStackItsBaseAndHowManyPullRequestsItHas() {
        BranchNode.Header open = (BranchNode.Header) BranchNode.of(
                        List.of(branch("feat/a"), branch("feat/b")),
                        branch -> Option.of(
                                branch.name().value().equals("feat/a")
                                        ? stacked(96, "feat/a", 1, ENTRIES)
                                        : stacked(97, "feat/b", 2, ENTRIES)),
                        true,
                        HashSet.empty(),
                        head -> Option.none())
                .head();

        assertEquals(
                "▾ stack #12 → main  3 PRs",
                LocalBranchesList.header(open, false, Option.none()).text());
        assertEquals(
                "▾ stack #12 → main  3 PRs  diverged: rebase it",
                LocalBranchesList.header(open, true, Option.none()).text());
        assertEquals(
                "▾ stack #12 → main  Pushing ⠋  3 PRs",
                LocalBranchesList.header(open, false, Option.of("Pushing ⠋")).text());
        assertEquals(Option.of(new BranchName("feat/b")), open.top());
        assertEquals(
                "▸ stack #12 → main  3 PRs",
                LocalBranchesList.header(
                                new BranchNode.Header(open.stack(), false, open.branches(), open.top()),
                                false,
                                Option.none())
                        .text());
    }

    @Test
    void aBranchInAStackIsIndentedOnBothItsLines() {
        LoadableList.Row row = LocalBranchesList.indented(new LoadableList.Row(
                List.of(LoadableList.Seg.of("feat/a")), Option.of(List.of(LoadableList.Seg.of("  4d")))));

        assertEquals("    feat/a", row.text());
        assertEquals(Option.of("      4d"), row.detailText());
    }
}
