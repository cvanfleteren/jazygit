package net.vanfleteren.jazygit.kernel.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;
import org.junit.jupiter.api.Test;

/**
 * The groups the features make of the local branches, joined as the branches pane lists them (ADR 167): a branch is in
 * the group of the first feature that groups it.
 */
class BranchGroupsTest {

    private static final BranchName A = new BranchName("a");
    private static final BranchName B = new BranchName("b");

    private static BranchGroups.Group group(String key, BranchName... members) {
        return new BranchGroups.Group(
                key,
                List.empty(),
                List.empty(),
                List.of(members).<BranchGroups.Entry>map(BranchGroups.Entry.Member::new),
                new BranchGroups.Keys<>(Scopes.BRANCHES, A));
    }

    private static BranchGroups.Grouping grouping(BranchGroups.Group... groups) {
        return (model, branches) -> List.of(groups);
    }

    @Test
    void aBranchStaysInTheGroupOfTheFirstFeatureThatGroupsIt() {
        BranchGroups groups = new BranchGroups(List.of(grouping(group("first", A)), grouping(group("second", A, B))));

        List<BranchGroups.Group> listed = groups.of(TestModels.loaded(), List.empty());

        assertEquals(List.of("first", "second"), listed.map(BranchGroups.Group::key));
        assertEquals(List.of(List.of(A), List.of(B)), listed.map(BranchGroups.Group::members));
    }

    @Test
    void aGroupWhoseBranchesEarlierGroupsHaveIsLeftOutEvenWithRowsThatAreNoBranch() {
        BranchGroups.Group elsewhere = new BranchGroups.Group(
                "second",
                List.empty(),
                List.empty(),
                List.of(
                        new BranchGroups.Entry.Member(B),
                        new BranchGroups.Entry.Elsewhere("x", "x", Option.none(), "x")),
                new BranchGroups.Keys<>(Scopes.BRANCHES, B));
        BranchGroups groups = new BranchGroups(
                List.of(grouping(group("first", A, B)), grouping(group("taken", A)), grouping(elsewhere)));

        List<BranchGroups.Group> listed = groups.of(TestModels.loaded(), List.empty());

        assertEquals(List.of("first"), listed.map(BranchGroups.Group::key));
    }

    @Test
    void theFillsOfEveryFeatureAreGroupedInTheirOrder() {
        Fills fills = Fills.NONE
                .with(BranchGroups.KEY, model -> Option.some(grouping(group("first", A))))
                .andThen(Fills.NONE.with(BranchGroups.KEY, model -> Option.none()))
                .andThen(Fills.NONE.with(BranchGroups.KEY, model -> Option.some(grouping(group("second", B)))));

        assertEquals(
                List.of("first", "second"),
                BranchGroups.filled(fills).of(TestModels.loaded(), List.empty()).map(BranchGroups.Group::key));
        assertEquals(List.empty(), BranchGroups.NONE.of(TestModels.loaded(), List.empty()));
    }
}
