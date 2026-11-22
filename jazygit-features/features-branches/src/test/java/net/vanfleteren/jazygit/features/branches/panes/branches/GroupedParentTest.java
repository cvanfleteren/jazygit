package net.vanfleteren.jazygit.features.branches.panes.branches;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.BaseDistance;
import net.vanfleteren.jazygit.core.git.model.Fixtures;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;
import org.junit.jupiter.api.Test;

/**
 * A branch in a group, as a stack on GitHub, whose place in it says what it is built on: it shows its parent only
 * when it lacks commits of it, which GitHub does not merge (ADR 73).
 */
class GroupedParentTest {

    private static final BranchName MAIN = new BranchName("main");
    private static final BranchName FEAT_C = new BranchName("feat/c");
    private static final BranchName FEAT_D = new BranchName("feat/d");

    private static final List<BranchGroups.Group> GROUPED = List.of(new BranchGroups.Group(
            "g",
            List.empty(),
            List.empty(),
            List.of(new BranchGroups.Entry.Member(FEAT_C), new BranchGroups.Entry.Member(FEAT_D)),
            new BranchGroups.Keys<>(Scopes.BRANCHES, FEAT_C)));

    private static Branch branch(BranchName name) {
        return Fixtures.branch(name, false, new Sha("aaaa"));
    }

    private static BaseDistance behind(BranchName base, int commits) {
        return new BaseDistance(new BranchRef.Remote(new RemoteBranch("origin", base)), new Sha("bbbb"), 1, commits);
    }

    // main ← feat/c ← feat/d; feat/d lags feat/c by dBehind, feat/c lags main by cBehind.
    private static Model model(int dBehind, int cBehind) {
        Model read = TestModels.localBranchesRead(
                        TestModels.loaded(), List.of(MAIN, FEAT_C, FEAT_D).map(GroupedParentTest::branch))
                .model();
        return read.mapRepo(repo -> repo.withParents(HashMap.of(FEAT_C, MAIN, FEAT_D, FEAT_C))
                .withBaseDistance(HashMap.of(FEAT_C, behind(MAIN, cBehind), FEAT_D, behind(FEAT_C, dBehind))));
    }

    @Test
    void aBranchIsBehindItsParentOnlyByTheCommitsOfThatParent() {
        assertEquals(Option.of(2), model(2, 0).repo().behindParent(FEAT_D));
        assertEquals(Option.none(), model(0, 0).repo().behindParent(FEAT_D));
        assertEquals(
                Option.none(),
                model(2, 0)
                        .mapRepo(repo -> repo.withBaseDistance(HashMap.of(FEAT_D, behind(MAIN, 2))))
                        .repo()
                        .behindParent(FEAT_D));
    }

    @Test
    void aGroupedBranchBehindTheOneBelowShowsItsParent() {
        assertEquals(Option.of(FEAT_C), LocalBranchesList.shownParent(model(2, 0), branch(FEAT_D), GROUPED));
    }

    @Test
    void aGroupedBranchOnlyBehindTheBaseShowsNone() {
        assertEquals(Option.none(), LocalBranchesList.shownParent(model(0, 5), branch(FEAT_D), GROUPED));
    }
}
