package net.vanfleteren.jazygit.features.branches.panes.branches;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.Fixtures;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.branches.panes.branches.BranchGroups.Entry;
import net.vanfleteren.jazygit.features.branches.panes.branches.BranchGroups.Group;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;
import net.vanfleteren.jazygit.kernel.ui.widgets.LoadableList;
import org.junit.jupiter.api.Test;

/**
 * The nodes of the local branches: a group a feature makes of branches is a header with what is in it under it
 * (ADR 72, ADR 167). The pane knows no feature: these groups are a test's own.
 */
class BranchNodeTest {

    private static Branch branch(String name) {
        return Fixtures.branch(new BranchName(name), false, new Sha("aaaa"));
    }

    private static Entry member(String name) {
        return new Entry.Member(new BranchName(name));
    }

    private static Entry elsewhere(String key, String text) {
        return new Entry.Elsewhere(key, text, Option.none(), key);
    }

    private static Group group(String key, Entry... entries) {
        return new Group(
                key,
                List.of(LoadableList.Seg.of("group " + key)),
                List.of(LoadableList.Seg.of(" 3 things")),
                List.of(entries),
                new BranchGroups.Keys<>(Scopes.BRANCHES, new BranchName("x")));
    }

    private static List<String> keys(List<Branch> branches, List<Group> groups, Set<String> closed) {
        return BranchNode.of(branches, groups, true, closed).map(BranchNode::key);
    }

    @Test
    void aGroupIsAHeaderWithWhatIsInItUnderItInItsOrder() {
        List<Branch> branches = List.of(branch("feat/c"), branch("feat/x"), branch("feat/a"), branch("feat/b"));
        Group group = group("g", member("feat/a"), member("feat/b"), member("feat/c"));

        assertEquals(
                List.of("g", "branch:feat/a", "branch:feat/b", "branch:feat/c", "branch:feat/x"),
                keys(branches, List.of(group), HashSet.empty()));
    }

    @Test
    void aGroupSitsWhereItsMostRecentBranchDoes() {
        List<Branch> branches = List.of(branch("feat/x"), branch("feat/b"), branch("main"), branch("feat/a"));
        Group group = group("g", member("feat/a"), member("feat/b"));

        assertEquals(
                List.of("branch:feat/x", "g", "branch:feat/a", "branch:feat/b", "branch:main"),
                keys(branches, List.of(group), HashSet.empty()));
    }

    @Test
    void whatIsInAGroupButNoBranchHereStillShowsDimmedUnderIt() {
        Group group = group(
                "g",
                elsewhere("pullRequest:95", "#95 ◆ merged"),
                elsewhere("pullRequest:96", "#96 not checked out"),
                member("feat/b"));

        List<BranchNode> nodes = BranchNode.of(List.of(branch("feat/b")), List.of(group), true, HashSet.empty());

        assertEquals(List.of("g", "pullRequest:95", "pullRequest:96", "branch:feat/b"), nodes.map(BranchNode::key));
        assertEquals(
                List.of("    #95 ◆ merged", "    #96 not checked out"),
                nodes.drop(1)
                        .take(2)
                        .map(node ->
                                LocalBranchesList.other((BranchNode.Other) node).text()));
    }

    @Test
    void ungroupedEveryBranchIsANodeOfItsOwnInItsOrder() {
        List<Branch> branches = List.of(branch("feat/c"), branch("feat/x"), branch("feat/a"), branch("feat/b"));
        Group group = group("g", member("feat/a"), member("feat/b"), member("feat/c"));

        List<BranchNode> nodes = BranchNode.of(branches, List.of(group), false, HashSet.of("g"));

        assertEquals(
                List.of("branch:feat/c", "branch:feat/x", "branch:feat/a", "branch:feat/b"),
                nodes.map(BranchNode::key));
        assertEquals(List.of(false, false, false, false), nodes.map(node -> ((BranchNode.Leaf) node).inGroup()));
    }

    @Test
    void aClosedGroupIsItsHeaderAlone() {
        List<Branch> branches = List.of(branch("feat/a"), branch("feat/b"), branch("feat/x"));
        Group group = group("g", member("feat/a"), member("feat/b"));

        assertEquals(List.of("g", "branch:feat/x"), keys(branches, List.of(group), HashSet.of("g")));
    }

    @Test
    void theHeaderShowsWhetherItIsOpenItsTitleThenWhatItsFeatureShowsAfterTheSpinner() {
        BranchNode.Header open = (BranchNode.Header) BranchNode.of(
                        List.of(branch("feat/a"), branch("feat/b")),
                        List.of(group("g", member("feat/a"), member("feat/b"))),
                        true,
                        HashSet.empty())
                .head();

        assertEquals(
                "▾ group g  3 things",
                LocalBranchesList.header(open, Option.none()).text());
        assertEquals(
                "▾ group g  Pushing ⠋  3 things",
                LocalBranchesList.header(open, Option.of("Pushing ⠋")).text());
        assertEquals(Option.of(new BranchName("feat/b")), open.top());
        assertEquals(
                "▸ group g  3 things",
                LocalBranchesList.header(
                                new BranchNode.Header(open.group(), false, open.branches(), open.top()), Option.none())
                        .text());
    }

    @Test
    void aBranchAnEarlierFeatureGroupedStaysInItsGroup() {
        List<Branch> branches = List.of(branch("feat/a"), branch("feat/b"));
        BranchGroups groups = new BranchGroups(List.of(
                (model, listed) -> List.of(group("first", member("feat/a"))),
                (model, listed) -> List.of(group("second", member("feat/a"), member("feat/b")))));

        List<Group> made = groups.of(TestModels.loaded(), branches);

        assertEquals(List.of("first", "second"), made.map(Group::key));
        assertEquals(List.of(new BranchName("feat/b")), made.last().members());
    }

    @Test
    void aBranchInAGroupIsIndentedOnBothItsLines() {
        LoadableList.Row row = LocalBranchesList.indented(new LoadableList.Row(
                List.of(LoadableList.Seg.of("feat/a")), Option.of(List.of(LoadableList.Seg.of("  4d")))));

        assertEquals("    feat/a", row.text());
        assertEquals(Option.of("      4d"), row.detailText());
    }
}
