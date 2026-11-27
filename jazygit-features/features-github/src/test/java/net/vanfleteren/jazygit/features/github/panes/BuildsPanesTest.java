package net.vanfleteren.jazygit.features.github.panes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;
import net.vanfleteren.jazygit.core.gh.model.Build;
import net.vanfleteren.jazygit.core.gh.model.BuildsPage;
import net.vanfleteren.jazygit.core.gh.model.RepoWorkflow;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.PrNumber;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.github.builds.BuildPages;
import net.vanfleteren.jazygit.features.github.builds.screen.BuildsScreen;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.ui.widgets.LoadableList;
import org.junit.jupiter.api.Test;

/**
 * The rows and titles of the panes of the builds screen, and its mark in the title of the status (ADR 212).
 */
class BuildsPanesTest {

    private static final Instant T = Instant.parse("2026-11-20T10:00:00Z");

    private static Build run(long id, String conclusion, Option<BranchName> branch, Option<PrNumber> pr) {
        Instant start = T.minusSeconds(600);
        return new Build(
                "Build",
                pr.isDefined() ? "pull_request" : "push",
                id,
                1,
                new Sha("abc" + id),
                "Commit " + id,
                start,
                List.of(new Build.Job(
                        "verify", conclusion, Option.of(start.plusSeconds(5)), Option.of(start.plusSeconds(65)))),
                Option.none(),
                new Build.Origin(Option.of("W_build"), branch, pr));
    }

    private static String text(List<LoadableList.Seg> segs) {
        return segs.map(LoadableList.Seg::text).mkString();
    }

    @Test
    void aRunShowsHowItEndedWhatItRanForItsEventHowLongAgainstTheMedianAndWhen() {
        Build failed = run(4, "FAILURE", Option.of(new BranchName("feature")), Option.of(new PrNumber(7)));

        assertEquals(
                "✗◆ #7 feature pr 1m05 +20s 10m",
                text(RunRows.row(failed, Option.of(Duration.ofSeconds(45)), HashSet.of(failed.sha()), T)));
        assertEquals("✗  #7 feature pr 1m05 10m", text(RunRows.row(failed, Option.none(), HashSet.empty(), T)));
    }

    @Test
    void aRunOfTheDefaultBranchSaysItsBranch() {
        assertEquals("main", RunRows.ranFor(run(1, "SUCCESS", Option.of(new BranchName("main")), Option.none())));
    }

    @Test
    void aWorkflowShowsHowItsLastBuildOnTheDefaultBranchEndedAndHowManyItHasThere() {
        BuildsScreen screen = BuildsScreen.INITIAL.withOnDefault(BuildPages.asked(25)
                .readCommits(Option.of(new BuildsPage(
                        List.of(
                                run(2, "FAILURE", Option.of(new BranchName("main")), Option.none()),
                                run(1, "SUCCESS", Option.of(new BranchName("main")), Option.none())),
                        2,
                        Option.none()))));
        RepoWorkflow build = new RepoWorkflow("W_build", "Build", ".github/workflows/build.yml", true);
        RepoWorkflow other = new RepoWorkflow("W_other", "Other", ".github/workflows/other.yml", true);

        assertEquals(
                "✗ Build 1m05 2", BuildsWorkflowsPanel.row(screen, build).text().stripTrailing());
        assertEquals("· Other", BuildsWorkflowsPanel.row(screen, other).text().stripTrailing());
    }

    @Test
    void theTitleOfTheRunsSaysWhichAndWhenMoreCanBeRead() {
        BuildsScreen every = BuildsScreen.INITIAL.withRuns(io.vavr.collection.HashMap.of(
                "W_build",
                BuildPages.asked(BuildPages.RUNS)
                        .read(Option.of(new net.vanfleteren.jazygit.core.gh.model.WorkflowRunsPage(
                                List.empty(), 100, Option.of("cursor"))))));
        BuildsScreen screen = every.withWorkflows(new Loadable.Loaded<>(
                        List.of(new RepoWorkflow("W_build", "Build", ".github/workflows/build.yml", true))))
                .withOnDefault(BuildPages.asked(25).readCommits(Option.none()));

        assertEquals("Runs of Build · every branch · m for more", BuildsRunsPanel.title(screen, "Build"));
        assertEquals(
                "Runs of Build · main",
                BuildsRunsPanel.title(
                        BuildsScreen.INITIAL
                                .withFilter(BuildsScreen.Filter.DEFAULT_BRANCH)
                                .withDefaultName(Option.of(new BranchName("main")))
                                .withOnDefault(BuildPages.asked(25).readCommits(Option.none())),
                        "Build"));
    }

    @Test
    void theHighlightGoesToTheNewestForAnotherWorkflowToARunClickedElseWithItsRun() {
        Build newer = run(2, "SUCCESS", Option.none(), Option.none());
        Build older = run(1, "SUCCESS", Option.none(), Option.none());
        BuildsRunsPanel.Listed before = new BuildsRunsPanel.Listed("Build", List.of(older), Option.none());

        assertEquals(
                1,
                BuildsRunsPanel.reselect(
                        before, new BuildsRunsPanel.Listed("Build", List.of(newer, older), Option.none()), 0));
        assertEquals(
                0,
                BuildsRunsPanel.reselect(
                        before, new BuildsRunsPanel.Listed("Publish", List.of(newer, older), Option.none()), 0));
        assertEquals(
                0,
                BuildsRunsPanel.reselect(
                        new BuildsRunsPanel.Listed("Build", List.of(newer, older), Option.none()),
                        new BuildsRunsPanel.Listed(
                                "Build", List.of(newer, older), Option.of(new BuildsScreen.Clicked(2, 1))),
                        1));
    }

    @Test
    void theStatusSaysTheDefaultBranchFailsAndHowManyRunsAreInProgress() {
        BuildsScreen failing = BuildsScreen.INITIAL
                .withWorkflows(new Loadable.Loaded<>(
                        List.of(new RepoWorkflow("W_build", "Build", ".github/workflows/build.yml", true))))
                .withOnDefault(BuildPages.asked(25)
                        .readCommits(Option.of(new BuildsPage(
                                List.of(run(2, "FAILURE", Option.of(new BranchName("main")), Option.none())),
                                1,
                                Option.none()))))
                .withInProgress(Option.of(2));
        Model model = TestModels.loaded().withPart(BuildsScreen.PART, failing);

        assertEquals(Option.of(" ✗ ●2"), BuildsScreenSpec.mark(model).map(LoadableList.Seg::text));
        assertEquals(Option.none(), BuildsScreenSpec.mark(TestModels.loaded()).map(LoadableList.Seg::text));
    }
}
