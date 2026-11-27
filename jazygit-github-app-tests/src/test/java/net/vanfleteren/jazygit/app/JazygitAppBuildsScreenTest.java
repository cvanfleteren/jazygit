package net.vanfleteren.jazygit.app;

import static net.vanfleteren.jazygit.app.TestApp.awaitModel;
import static net.vanfleteren.jazygit.app.TestApp.focusedId;
import static net.vanfleteren.jazygit.app.TestApp.onRenderThread;
import static net.vanfleteren.jazygit.app.TestApp.tickless;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.tui.event.KeyModifiers;
import dev.tamboui.tui.pilot.Pilot;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.gh.StubGitHub;
import net.vanfleteren.jazygit.core.gh.checks.StubChecks;
import net.vanfleteren.jazygit.core.gh.model.Build;
import net.vanfleteren.jazygit.core.gh.model.BuildsPage;
import net.vanfleteren.jazygit.core.gh.model.DefaultBranchPage;
import net.vanfleteren.jazygit.core.gh.model.RepoWorkflow;
import net.vanfleteren.jazygit.core.gh.model.WorkflowRunsPage;
import net.vanfleteren.jazygit.core.gh.process.GhError;
import net.vanfleteren.jazygit.core.git.model.SampleData;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.PrNumber;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.github.GitHubServices;
import net.vanfleteren.jazygit.features.github.builds.screen.BuildsScreen;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.ui.RenderedText;
import net.vanfleteren.jazygit.kernel.ui.SettledPilot;
import org.junit.jupiter.api.Test;

/**
 * The builds screen, driven through the app (ADR 212): ctrl+b shows it on its runs, with the workflows above them and
 * the runs of the one highlighted in the content panel, and b chooses which runs show.
 */
class JazygitAppBuildsScreenTest {

    private static final BranchName MAIN = new BranchName("main");
    private static final Instant AT = Instant.parse("2026-11-20T08:00:00Z");

    private static Build run(String workflow, long id, String conclusion, BranchName branch) {
        Instant start = AT.minusSeconds(600 * id);
        return new Build(
                workflow,
                branch.equals(MAIN) ? "push" : "pull_request",
                id,
                1,
                new Sha("abcdef0"),
                "Commit " + id,
                start,
                List.of(new Build.Job(
                        "verify", conclusion, Option.of(start.plusSeconds(5)), Option.of(start.plusSeconds(245)))),
                Option.none(),
                new Build.Origin(
                        Option.of("W_" + workflow),
                        Option.of(branch),
                        Option.when(!branch.equals(MAIN), new PrNumber(7))));
    }

    private static Env env() {
        return new Env(GitHubKeys.onGitHub(SampleData.git()))
                .with(GitHubServices.GH, StubGitHub.of().withChecks(new StubChecks() {
                    @Override
                    public Either<GhError, List<RepoWorkflow>> workflows() {
                        return Either.right(List.of(
                                new RepoWorkflow("W_Build", "Build", ".github/workflows/build.yml", true),
                                new RepoWorkflow("W_Publish", "Publish", ".github/workflows/publish.yml", true)));
                    }

                    @Override
                    public Either<GhError, Option<DefaultBranchPage>> defaultBranchBuilds(Option<String> after) {
                        return Either.right(Option.of(new DefaultBranchPage(
                                MAIN,
                                new BuildsPage(
                                        List.of(run("Build", 1, "FAILURE", MAIN), run("Publish", 2, "SUCCESS", MAIN)),
                                        1,
                                        Option.none()))));
                    }

                    @Override
                    public Either<GhError, Option<WorkflowRunsPage>> workflowRuns(
                            String workflowId, Option<String> after) {
                        return Either.right(Option.of(new WorkflowRunsPage(
                                List.of(
                                        run("Build", 3, "SUCCESS", new BranchName("feature")),
                                        run("Build", 1, "FAILURE", MAIN)),
                                2,
                                Option.none())));
                    }

                    @Override
                    public Either<GhError, Integer> runsInProgress() {
                        return Either.right(2);
                    }
                }));
    }

    private static String screen(ToolkitTestRunner testRunner, TestApp app) throws Exception {
        return RenderedText.ofSize(testRunner, app::frame, TestApp.SIZE.width(), TestApp.SIZE.height());
    }

    @Test
    void ctrlBShowsTheRunsOfTheWorkflowThatFailedOnTheDefaultBranchAndBChoosesWhichRuns() throws Exception {
        TestApp app = new TestApp(env());
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(app::frame, TestApp.SIZE, tickless())) {
            app.start(testRunner);
            Pilot pilot = SettledPilot.of(testRunner, app.drawnAt::size);

            pilot.press('b', KeyModifiers.CTRL);
            awaitModel(
                    testRunner,
                    app,
                    model -> model.part(BuildsScreen.PART)
                                    .filtered()
                                    .getOrElse(List.empty())
                                    .size()
                            == 2);
            SettledPilot.settle(testRunner, app.drawnAt::size);
            assertEquals(BuildsScreen.RUNS_PANE.value(), onRenderThread(testRunner, () -> focusedId(testRunner)));

            String every = screen(testRunner, app);
            assertTrue(every.contains("Builds ✗ ●2"), every);
            assertTrue(every.contains("Workflows"), every);
            assertTrue(every.contains("✗ Build"), every);
            assertTrue(every.contains("Runs of Build · every branch"), every);
            assertTrue(every.contains("#7 feature"), every);
            assertTrue(every.contains("main ✗ failing for"), every);

            pilot.press('b');
            awaitModel(
                    testRunner,
                    app,
                    model -> model.popup(net.vanfleteren.jazygit.features.github.builds.screen.FilterPopup.class)
                            .isDefined());
            pilot.press('d');
            awaitModel(
                    testRunner,
                    app,
                    model -> model.part(BuildsScreen.PART).filter() == BuildsScreen.Filter.DEFAULT_BRANCH);
            SettledPilot.settle(testRunner, app.drawnAt::size);

            String onMain = screen(testRunner, app);
            assertTrue(onMain.contains("Runs of Build · main"), onMain);
        } finally {
            app.onStop();
        }
    }
}
