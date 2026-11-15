package net.vanfleteren.jazygit.state.view;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.Build;
import net.vanfleteren.jazygit.git.model.Ahead;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.Fixtures;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.view.BuildsView.Pipeline;
import org.junit.jupiter.api.Test;

/**
 * Which workflow's builds show, in which order the tabs show them, and which builds show (ADR 131).
 */
class BuildsViewTest {

    private static final String TIP = "a".repeat(40);
    private static final String OLDER = "b".repeat(40);
    private static final Pipeline PUBLISH = new Pipeline("Publish", "push");
    private static final Pipeline BUILD = new Pipeline("Build", "pull_request");
    private static final Pipeline MARKDOWN = new Pipeline("Markdown", "pull_request");
    private static final Pipeline NIX = new Pipeline("Nix", "push");

    private static Build build(Pipeline pipeline, String sha, String conclusion) {
        Instant at = Instant.parse("2026-11-15T08:00:00Z");
        return new Build(
                pipeline.workflow(),
                pipeline.event(),
                1,
                1,
                sha,
                "Title",
                at,
                List.of(new Build.Job("job", conclusion, Option.of(at), Option.of(at.plusSeconds(60)))));
    }

    // The tip ran Build and Markdown; the older commit ran Publish three times and Nix once.
    private static final List<Build> BUILDS = List.of(
            build(BUILD, TIP, "SUCCESS"),
            build(MARKDOWN, TIP, "FAILURE"),
            build(PUBLISH, OLDER, "SUCCESS"),
            build(PUBLISH, OLDER, "SUCCESS"),
            build(PUBLISH, OLDER, "SUCCESS"),
            build(NIX, OLDER, "SUCCESS"));

    private static BuildsView view() {
        return BuildsView.reading(new BranchName("feature"), new RemoteBranch("origin", new BranchName("feature")));
    }

    @Test
    void theWorkflowsOnTheTipComeFirstThenByHowManyBuildsTheyHave() {
        assertEquals(List.of(BUILD, MARKDOWN, PUBLISH, NIX), BuildsView.pipelines(BUILDS));
    }

    @Test
    void ofThoseOnTheTipOneThatFailedShowsUnlessOneWasChosen() {
        assertEquals(Option.of(MARKDOWN), view().shownPipeline(BUILDS));
        assertEquals(Option.of(NIX), view().withSelected(NIX).shownPipeline(BUILDS));
    }

    @Test
    void aChosenWorkflowWithoutBuildsGivesWayToTheOneOnTheTip() {
        assertEquals(
                Option.of(MARKDOWN),
                view().withSelected(new Pipeline("Gone", "push")).shownPipeline(BUILDS));
    }

    @Test
    void steppingGoesToTheNextTabAndAroundFromTheLast() {
        assertEquals(Option.of(PUBLISH), view().stepped(BUILDS, 1).shownPipeline(BUILDS));
        assertEquals(Option.of(BUILD), view().stepped(BUILDS, -1).shownPipeline(BUILDS));
        assertEquals(
                Option.of(BUILD), view().withSelected(NIX).stepped(BUILDS, 1).shownPipeline(BUILDS));
    }

    @Test
    void aWorkflowThatRunsForTwoEventsIsNamedWithTheEvent() {
        List<Pipeline> all = List.of(BUILD, new Pipeline("Build", "push"), NIX);

        assertEquals("Build · pull_request", BUILD.label(all));
        assertEquals("Nix", NIX.label(all));
    }

    @Test
    void withOnlyTheCommitsNotOnTheBaseOnlyTheirBuildsShow() {
        Commit tip = Fixtures.commit(new Sha(TIP), "A", "a@a", Instant.EPOCH, "Tip", "");
        Ahead ahead = new Ahead(
                new Sha(TIP), new BranchRef.Local(new BranchName("main")), List.of(tip), false, Option.none());

        assertEquals(BUILDS.take(2), BuildsView.shown(BUILDS, Option.of(ahead), true));
        assertEquals(BUILDS, BuildsView.shown(BUILDS, Option.of(ahead), false));
        assertEquals(BUILDS, BuildsView.shown(BUILDS, Option.none(), true));
    }
}
