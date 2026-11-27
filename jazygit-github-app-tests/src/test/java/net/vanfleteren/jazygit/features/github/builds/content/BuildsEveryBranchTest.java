package net.vanfleteren.jazygit.features.github.builds.content;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.text.Span;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Instant;
import java.time.ZoneOffset;
import net.vanfleteren.jazygit.core.gh.StubGitHub;
import net.vanfleteren.jazygit.core.gh.checks.StubChecks;
import net.vanfleteren.jazygit.core.gh.model.Build;
import net.vanfleteren.jazygit.core.gh.model.BuildsPage;
import net.vanfleteren.jazygit.core.gh.model.RepoWorkflow;
import net.vanfleteren.jazygit.core.gh.model.WorkflowRunsPage;
import net.vanfleteren.jazygit.core.gh.process.GhError;
import net.vanfleteren.jazygit.core.git.StubGit;
import net.vanfleteren.jazygit.core.git.model.Fixtures;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.PrNumber;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.core.repo.Upstream;
import net.vanfleteren.jazygit.features.github.GitHubFailure;
import net.vanfleteren.jazygit.features.github.GitHubServices;
import net.vanfleteren.jazygit.features.github.builds.BuildsMsg;
import net.vanfleteren.jazygit.features.github.builds.BuildsShown;
import net.vanfleteren.jazygit.features.github.builds.BuildsView;
import net.vanfleteren.jazygit.features.github.builds.BuildsView.Pipeline;
import net.vanfleteren.jazygit.features.github.builds.LoadRepoWorkflows;
import net.vanfleteren.jazygit.features.github.builds.LoadWorkflowRuns;
import net.vanfleteren.jazygit.features.github.builds.Paging;
import net.vanfleteren.jazygit.features.github.builds.RunsDrawn;
import net.vanfleteren.jazygit.features.github.builds.WorkflowRuns;
import net.vanfleteren.jazygit.kernel.content.ContentViews;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.load.SelectionMsg;
import net.vanfleteren.jazygit.kernel.ui.content.Shown;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import org.junit.jupiter.api.Test;

/**
 * The runs of a workflow on every branch, the branch's own marked, and how long the workflow takes on every branch
 * next to the builds of the branch (ADR 131).
 */
class BuildsEveryBranchTest {

    private static final BranchName MAIN = new BranchName("main");
    private static final RemoteBranch ORIGIN_MAIN = new RemoteBranch("origin", MAIN);
    private static final Instant AT = Instant.parse("2026-11-15T08:00:00Z");
    private static final String BUILD_ID = "W_build";

    /**
     * A run of {@code workflow} on the commit {@code sha} that took {@code took} seconds, for {@code pullRequest}.
     */
    private static Build run(
            String workflow, String id, long runId, String sha, long took, Option<Integer> pullRequest) {
        return new Build(
                workflow,
                "pull_request",
                runId,
                1,
                new Sha(sha),
                "Commit " + runId,
                AT.plusSeconds(600 * runId),
                List.of(new Build.Job(
                        "job",
                        "SUCCESS",
                        Option.of(AT.plusSeconds(600 * runId)),
                        Option.of(AT.plusSeconds(600 * runId + took)))),
                Option.none(),
                new Build.Origin(Option.of(id), Option.none(), pullRequest.map(PrNumber::new)));
    }

    // The branch's own build of Build, on its commit "mine".
    private static final Build MINE = run("Build", BUILD_ID, 9, "mine", 200, Option.of(496));

    private static Model model() {
        Branch main = Fixtures.branch(
                MAIN,
                false,
                new Sha("abc"),
                Instant.EPOCH,
                true,
                false,
                Option.of(new Upstream(new BranchRef.Remote(ORIGIN_MAIN), 0, 0)));
        Model read =
                TestModels.localBranchesRead(TestModels.loaded(), List.of(main)).model();
        BranchRef local = new BranchRef.Local(MAIN);
        Model highlighted = Update.update(
                        Update.update(read, new SelectionMsg.BranchSelected(local))
                                .model(),
                        TestModels.branchLogLoaded(local, List.empty()))
                .model();
        return Update.update(highlighted, new BuildsMsg.Toggled(MAIN)).model();
    }

    private static BuildsView view(Model model) {
        return model.part(BuildsView.PART).get();
    }

    private static BuildsShown shown(Model model) {
        return ((BuildsBody) ((Shown.Area) ContentViews.of(model, Scopes.BRANCHES.pane())).body()).shown();
    }

    @Test
    void theBranchesBuildsReadHowLongTheirWorkflowTakesOnEveryBranch() {
        Next read = Update.update(
                model(),
                new BuildsMsg.Read(
                        ORIGIN_MAIN, Option.none(), Option.of(new BuildsPage(List.of(MINE), 3, Option.none()))));

        assertEquals(List.of(new LoadWorkflowRuns(BUILD_ID, Option.none())), read.cmds());

        Model runs = Update.update(
                        read.model(),
                        new BuildsMsg.RunsRead(
                                BUILD_ID,
                                Option.none(),
                                Option.of(new WorkflowRunsPage(
                                        List.of(
                                                MINE,
                                                run("Build", BUILD_ID, 8, "other", 300, Option.of(494)),
                                                run("Build", BUILD_ID, 7, "other2", 400, Option.none())),
                                        3,
                                        Option.none()))))
                .model();
        assertEquals(
                Option.of(new RunsDrawn.Baseline(java.time.Duration.ofSeconds(300), 3)),
                shown(runs).baseline());
    }

    @Test
    void aShowsTheRunsOnEveryBranchWithTheWorkflowsOfTheRepository() {
        Model read = Update.update(
                        model(),
                        new BuildsMsg.Read(
                                ORIGIN_MAIN, Option.none(), Option.of(new BuildsPage(List.of(MINE), 3, Option.none()))))
                .model();

        Next every = Update.update(read, new BuildsMsg.AllToggled());

        assertTrue(view(every.model()).allBranches());
        assertEquals(List.of(new LoadRepoWorkflows()), every.cmds());
        assertEquals(
                "Builds: main · last 3 commits · every branch",
                ((Shown.Area) ContentViews.of(every.model(), Scopes.BRANCHES.pane())).title());
        assertInstanceOf(Loadable.Loading.class, shown(every.model()).builds());

        Model listed = Update.update(
                        every.model(),
                        new BuildsMsg.RepoWorkflowsRead(List.of(
                                new RepoWorkflow(BUILD_ID, "Build", ".github/workflows/build.yml", true),
                                new RepoWorkflow("W_lint", "Lint", ".github/workflows/lint.yml", true),
                                new RepoWorkflow("W_old", "Old", ".github/workflows/old.yml", false))))
                .model();
        assertEquals(
                List.of(Pipeline.any("Build"), Pipeline.any("Lint")),
                shown(listed).tabs());

        Next lint = Update.update(listed, new BuildsMsg.TabClicked(Pipeline.any("Lint")));
        assertEquals(List.of(new LoadWorkflowRuns("W_lint", Option.none())), lint.cmds());
        assertEquals(Pipeline.any("Lint"), shown(lint.model()).shown());
    }

    @Test
    void theRunsOnEveryBranchAreReadOnUpToAHundred() {
        Model read = Update.update(
                        model(),
                        new BuildsMsg.Read(
                                ORIGIN_MAIN, Option.none(), Option.of(new BuildsPage(List.of(MINE), 3, Option.none()))))
                .model();

        Next first = Update.update(
                read,
                new BuildsMsg.RunsRead(
                        BUILD_ID, Option.none(), Option.of(new WorkflowRunsPage(List.of(MINE), 25, Option.of("c25")))));
        Next stale = Update.update(
                first.model(),
                new BuildsMsg.RunsRead(
                        BUILD_ID, Option.of("c75"), Option.of(new WorkflowRunsPage(List.of(MINE), 25, Option.none()))));

        assertTrue(first.cmds().contains(new LoadWorkflowRuns(BUILD_ID, Option.of("c25"))));
        assertSame(first.model(), stale.model());
        WorkflowRuns runs = view(first.model()).runs().get(BUILD_ID).get();
        assertEquals(25, runs.count());
        assertTrue(runs.reading());
    }

    @Test
    void mOnEveryBranchReadsAHundredMoreRunsOfTheTab() {
        Model read = Update.update(
                        model(),
                        new BuildsMsg.Read(
                                ORIGIN_MAIN, Option.none(), Option.of(new BuildsPage(List.of(MINE), 3, Option.none()))))
                .model();
        Model every = Update.update(read, new BuildsMsg.AllToggled()).model();
        Model hundred = Update.update(
                        every,
                        new BuildsMsg.RunsRead(
                                BUILD_ID,
                                Option.none(),
                                Option.of(new WorkflowRunsPage(List.of(MINE), 100, Option.of("c100")))))
                .model();
        assertEquals(
                "Builds: main · last 3 commits · every branch · m for more",
                ((Shown.Area) ContentViews.of(hundred, Scopes.BRANCHES.pane())).title());

        Next more = Update.update(hundred, new BuildsMsg.MoreRequested());

        assertEquals(List.of(new LoadWorkflowRuns(BUILD_ID, Option.of("c100"))), more.cmds());
        WorkflowRuns runs = view(more.model()).runs().get(BUILD_ID).get();
        assertTrue(runs.reading());
        assertEquals(200, runs.limit());
        assertFalse(view(more.model()).reading());
    }

    /**
     * The builds of the branch, read, shown on every branch.
     */
    private static Model everyBranch() {
        Model read = Update.update(
                        model(),
                        new BuildsMsg.Read(
                                ORIGIN_MAIN, Option.none(), Option.of(new BuildsPage(List.of(MINE), 3, Option.none()))))
                .model();
        return Update.update(read, new BuildsMsg.AllToggled()).model();
    }

    private static String title(Model model) {
        return ((Shown.Area) ContentViews.of(model, Scopes.BRANCHES.pane())).title();
    }

    private static final Failure TIMEOUT = GitHubFailure.of(new GhError.UnexpectedOutput("timeout"));

    @Test
    void mOnEveryBranchReadsAgainFromWhereReadingTheRunsFailed() {
        Model first = Update.update(
                        everyBranch(),
                        new BuildsMsg.RunsRead(
                                BUILD_ID,
                                Option.none(),
                                Option.of(new WorkflowRunsPage(List.of(MINE), 25, Option.of("c25")))))
                .model();
        assertEquals("Builds: main · last 3 commits · every branch · reading, 25 runs so far", title(first));
        Model stopped = Update.update(first, new BuildsMsg.RunsFailed(BUILD_ID, Option.of("c25"), TIMEOUT))
                .model();
        assertEquals("Builds: main · last 3 commits · every branch · m to try again", title(stopped));

        Next again = Update.update(stopped, new BuildsMsg.MoreRequested());

        assertEquals(List.of(new LoadWorkflowRuns(BUILD_ID, Option.of("c25"))), again.cmds());
        WorkflowRuns runs = view(again.model()).runs().get(BUILD_ID).get();
        assertEquals(new Paging.Reading(Option.of("c25")), runs.paging());
        assertEquals(125, runs.limit());
        assertEquals("Builds: main · last 3 commits · every branch · reading, 25 runs so far", title(again.model()));
    }

    @Test
    void mOnEveryBranchReadsAgainFromTheNewestWhenTheFirstRunsFailed() {
        Model failed = Update.update(everyBranch(), new BuildsMsg.RunsFailed(BUILD_ID, Option.none(), TIMEOUT))
                .model();
        assertEquals("Builds: main · last 3 commits · every branch · m to try again", title(failed));

        Next again = Update.update(failed, new BuildsMsg.MoreRequested());

        assertEquals(List.of(new LoadWorkflowRuns(BUILD_ID, Option.none())), again.cmds());
        WorkflowRuns runs = view(again.model()).runs().get(BUILD_ID).get();
        assertTrue(runs.reading());
        assertInstanceOf(Loadable.Loading.class, runs.builds());
        assertInstanceOf(Loadable.Loading.class, shown(again.model()).builds());
    }

    @Test
    void mOnEveryBranchReadsNothingWhileTheRunsAreReadOrAtTheOldest() {
        Model reading = everyBranch();
        Next whileReading = Update.update(reading, new BuildsMsg.MoreRequested());
        assertSame(reading, whileReading.model());
        assertTrue(whileReading.cmds().isEmpty());

        Model oldest = Update.update(
                        reading,
                        new BuildsMsg.RunsRead(
                                BUILD_ID,
                                Option.none(),
                                Option.of(new WorkflowRunsPage(List.of(MINE), 40, Option.none()))))
                .model();
        Next atOldest = Update.update(oldest, new BuildsMsg.MoreRequested());
        assertSame(oldest, atOldest.model());
        assertTrue(atOldest.cmds().isEmpty());
        assertEquals("Builds: main · last 3 commits · every branch", title(oldest));
    }

    @Test
    void theRunsOfEveryBranchMarkTheBranchesOwnAndSayWhatEachRanFor() {
        BuildsView view = BuildsView.reading(MAIN, ORIGIN_MAIN)
                .read(Option.of(new BuildsPage(List.of(MINE), 3, Option.none())))
                .withAllBranches(true)
                .withRuns(io.vavr.collection.HashMap.of(
                        BUILD_ID,
                        WorkflowRuns.asked()
                                .read(Option.of(new WorkflowRunsPage(
                                        List.of(MINE, run("Build", BUILD_ID, 8, "other", 300, Option.of(494))),
                                        2,
                                        Option.none())))));

        List<String> lines = BuildsText.lines(view.shownOf().get(), ZoneOffset.UTC, Theme.SpanStyles.NONE, 100)
                .map(line -> List.ofAll(line.spans()).map(Span::content).mkString());

        assertTrue(lines.get(2).startsWith("Build · every branch · 2 builds"), lines.mkString("\n"));
        assertTrue(lines.exists(line -> line.contains("◆ this branch")), lines.mkString("\n"));
        assertTrue(lines.exists(line -> line.startsWith("✓ ◆ #496")), lines.mkString("\n"));
        assertTrue(lines.exists(line -> line.startsWith("✓   #494")), lines.mkString("\n"));
    }

    @Test
    void theRunsAndTheWorkflowsAreReadFromGitHub() {
        Env env = new Env(StubGit.of()).with(GitHubServices.GH, StubGitHub.of().withChecks(new StubChecks() {
            @Override
            public Either<GhError, Option<WorkflowRunsPage>> workflowRuns(String id, Option<String> after) {
                return Either.right(Option.of(new WorkflowRunsPage(List.of(MINE), 1, Option.none())));
            }

            @Override
            public Either<GhError, List<RepoWorkflow>> workflows() {
                return Either.left(new GhError.UnexpectedOutput("no"));
            }
        }));

        assertEquals(
                new BuildsMsg.RunsRead(
                        BUILD_ID, Option.none(), Option.of(new WorkflowRunsPage(List.of(MINE), 1, Option.none()))),
                new LoadWorkflowRuns(BUILD_ID, Option.none()).run(env));
        assertInstanceOf(BuildsMsg.RepoWorkflowsFailed.class, new LoadRepoWorkflows().run(env));
    }
}
