package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.git.model.*;
import org.junit.jupiter.api.Test;

class RemoteBranchesListTest {

    private static final RemoteRef FEATURE =
            new RemoteRef(new RemoteBranch("origin", new Branch.Name("feature")), new Sha("2"), Instant.EPOCH);

    @Test
    void aRemoteBranchThatALocalBranchFollowsIsDimmed() {
        LoadableList.Row tracked =
                RemoteBranchesList.row(FEATURE, Duration.ofMinutes(5), true, List.empty(), Option.none());
        LoadableList.Row untracked =
                RemoteBranchesList.row(FEATURE, Duration.ofMinutes(5), false, List.empty(), Option.none());

        assertEquals("  5m origin/feature", tracked.text());
        assertEquals(List.of(Option.of(Theme.Classes.DIM)), tracked.segs().map(LoadableList.Seg::cssClass));
        assertEquals("  5m origin/feature", untracked.text());
        assertEquals(List.of(Option.none()), untracked.segs().map(LoadableList.Seg::cssClass));
    }

    @Test
    void aRemoteBranchShowsItsPullRequestThenItsSpinner() {
        LoadableList.Row row = RemoteBranchesList.row(
                FEATURE,
                Duration.ofMinutes(5),
                false,
                BranchSegs.gitHub(Option.of(new PullRequest(42, "b", false, Checks.NONE, Review.NONE)), Option.none()),
                Option.of("Pushing |"));

        assertEquals("  5m origin/feature #42 Pushing |", row.text());
        assertEquals(
                List.of(Option.none(), Option.of(Theme.Classes.PULL_REQUEST), Option.of(Theme.Classes.SPINNER)),
                row.segs().map(LoadableList.Seg::cssClass));
    }
}
