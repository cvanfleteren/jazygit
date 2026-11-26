package net.vanfleteren.jazygit.features.branches.panes.branches;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;
import net.vanfleteren.jazygit.core.git.model.*;
import net.vanfleteren.jazygit.core.repo.*;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import net.vanfleteren.jazygit.kernel.ui.widgets.LoadableList;
import org.junit.jupiter.api.Test;

class RemoteBranchesListTest {

    private static final RemoteRef FEATURE =
            new RemoteRef(new RemoteBranch("origin", new BranchName("feature")), new Sha("2"), Instant.EPOCH);

    @Test
    void aRemoteBranchThatALocalBranchFollowsIsDimmed() {
        LoadableList.Row tracked =
                RemoteBranchesList.row(FEATURE, Duration.ofMinutes(5), true, List.empty(), Option.none());
        LoadableList.Row untracked =
                RemoteBranchesList.row(FEATURE, Duration.ofMinutes(5), false, List.empty(), Option.none());

        assertEquals("origin/feature", tracked.text());
        assertEquals(List.of(Option.of(Theme.Classes.DIM)), tracked.segs().map(LoadableList.Seg::cssClass));
        assertEquals("origin/feature", untracked.text());
        assertEquals(List.of(Option.none()), untracked.segs().map(LoadableList.Seg::cssClass));
        // The age below the name has no colour of its own, so it is dimmed either way.
        assertEquals(Option.of("  5m"), tracked.detailText());
    }

    @Test
    void aRemoteBranchShowsItsSpinnerBesideItsNameAndItsPullRequestBelowIt() {
        LoadableList.Row row = RemoteBranchesList.row(
                FEATURE, Duration.ofMinutes(5), false, List.of(LoadableList.Seg.of(" #42")), Option.of("Pushing |"));

        assertEquals("origin/feature  Pushing |", row.text());
        assertEquals(
                List.of(Option.none(), Option.of(Theme.Classes.SPINNER)),
                row.segs().map(LoadableList.Seg::cssClass));
        assertEquals(Option.of("  5m  #42"), row.detailText());
    }
}
