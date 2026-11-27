package net.vanfleteren.jazygit.features.forgejo.badges;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import net.vanfleteren.jazygit.core.forgejo.CommitStatus;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.forgejo.builds.BuildsUpdateTest;
import net.vanfleteren.jazygit.features.forgejo.builds.ForgejoBuilds;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.ui.BranchBadges;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import net.vanfleteren.jazygit.kernel.ui.widgets.LoadableList;
import org.junit.jupiter.api.Test;

class ForgejoBadgeTest {

    private static final Sha MAIN = new Sha("1".repeat(40));
    private static final Sha FEATURE = new Sha("2".repeat(40));

    private static Model read(CommitStatus.State main, CommitStatus.State feature) {
        return BuildsUpdateTest.model()
                .withPart(
                        ForgejoBuilds.PART,
                        ForgejoBuilds.NONE.read(
                                List.empty(), HashMap.of(MAIN, main, FEATURE, feature), HashSet.empty()));
    }

    private static BranchBadges.Shown remote(Model model, String name) {
        return ForgejoBadge.of(model, new BranchRef.Remote(new RemoteBranch("origin", new BranchName(name))));
    }

    @Test
    void aLocalBranchShowsTheLastBuildOfTheBranchItFollows() {
        assertEquals(
                List.of(LoadableList.Seg.styled(" ✗", Theme.Classes.CHECKS_FAILED)),
                ForgejoBadge.of(
                                read(CommitStatus.State.PASSED, CommitStatus.State.FAILED),
                                new BranchRef.Local(new BranchName("feature")))
                        .segs());
    }

    @Test
    void aRemoteBranchShowsItsOwnWithTheSymbolsOfTheChecks() {
        Model model = read(CommitStatus.State.PASSED, CommitStatus.State.RUNNING);
        assertEquals(
                List.of(LoadableList.Seg.styled(" ✓", Theme.Classes.CHECKS_PASSED)),
                remote(model, "main").segs());
        assertEquals(
                List.of(LoadableList.Seg.styled(" ●", Theme.Classes.CHECKS_RUNNING)),
                remote(model, "feature").segs());
    }

    @Test
    void nothingShowsForACommitNotReadOrWithoutBuilds() {
        assertEquals(BranchBadges.Shown.NONE, remote(BuildsUpdateTest.model(), "main"));
        assertEquals(BranchBadges.Shown.NONE, remote(read(CommitStatus.State.NONE, CommitStatus.State.NONE), "main"));
        assertEquals(
                BranchBadges.Shown.NONE,
                ForgejoBadge.of(BuildsUpdateTest.model(), new BranchRef.Local(new BranchName("unknown"))));
    }
}
