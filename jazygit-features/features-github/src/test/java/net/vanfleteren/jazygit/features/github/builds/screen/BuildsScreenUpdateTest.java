package net.vanfleteren.jazygit.features.github.builds.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.gh.model.Build;
import net.vanfleteren.jazygit.core.gh.model.BuildsPage;
import net.vanfleteren.jazygit.core.gh.model.DefaultBranchPage;
import net.vanfleteren.jazygit.core.gh.model.RepoWorkflow;
import net.vanfleteren.jazygit.core.gh.model.WorkflowRunsPage;
import net.vanfleteren.jazygit.core.git.model.Fixtures;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.PrNumber;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.core.repo.Upstream;
import net.vanfleteren.jazygit.features.github.PullRequestFixtures;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.preferences.Screen;
import net.vanfleteren.jazygit.kernel.state.preferences.ScreenMsg;
import org.junit.jupiter.api.Test;

/**
 * The builds screen (ADR 212): what it reads while it is shown, which workflow it starts on, its filter, and reading
 * more and again.
 */
class BuildsScreenUpdateTest {

    private static final Instant T = Instant.parse("2026-11-20T10:00:00Z");
    private static final BranchName MAIN = new BranchName("main");
    private static final BranchName FEATURE = new BranchName("feature");
    private static final RemoteBranch ORIGIN_FEATURE = new RemoteBranch("origin", FEATURE);

    private static final RepoWorkflow BUILD = new RepoWorkflow("W_build", "Build", ".github/workflows/build.yml", true);
    private static final RepoWorkflow PUBLISH =
            new RepoWorkflow("W_publish", "Publish", ".github/workflows/publish.yml", true);
    private static final RepoWorkflow OLD = new RepoWorkflow("W_old", "Old", ".github/workflows/old.yml", false);

    /**
     * The run {@code id} of {@code workflow} on {@code branch}, which ended as {@code conclusion}, {@code id} minutes
     * before {@link #T}.
     */
    private static Build run(String workflow, long id, String conclusion, BranchName branch) {
        Instant start = T.minusSeconds(60 * id);
        return new Build(
                workflow,
                branch.equals(MAIN) ? "push" : "pull_request",
                id,
                1,
                // The first two runs are on one commit, the tip of the default branch.
                new Sha("abcdef" + Math.max(2, id)),
                "Commit " + id,
                start,
                List.of(new Build.Job(
                        "verify", conclusion, Option.of(start.plusSeconds(5)), Option.of(start.plusSeconds(50)))),
                Option.none(),
                new Build.Origin(
                        Option.of("W_" + workflow.toLowerCase()),
                        Option.of(branch),
                        Option.when(!branch.equals(MAIN), new PrNumber(7))));
    }

    // The default branch: Publish passed on its tip, Build failed there and passed before.
    private static final List<Build> ON_MAIN = List.of(
            run("Publish", 1, "SUCCESS", MAIN), run("Build", 2, "FAILURE", MAIN), run("Build", 3, "SUCCESS", MAIN));

    private static Model model() {
        Branch feature = Fixtures.branch(
                FEATURE,
                true,
                new Sha("f00"),
                Instant.EPOCH,
                true,
                false,
                Option.of(new Upstream(new BranchRef.Remote(ORIGIN_FEATURE), 0, 0)));
        Branch local = Fixtures.branch(new BranchName("local"), false, new Sha("100"));
        return TestModels.localBranchesRead(PullRequestFixtures.loadedWithTheFeature(), List.of(feature, local))
                .model();
    }

    private static Next shown(Model model) {
        return Update.update(model, new ScreenMsg.Toggled(BuildsScreen.SCREEN));
    }

    /**
     * The screen shown, with the workflows and the builds of the default branch read.
     */
    private static Next read(Model model) {
        Model shown = shown(model).model();
        Model workflows = Update.update(shown, new BuildsScreenMsg.WorkflowsRead(List.of(PUBLISH, BUILD, OLD)))
                .model();
        return Update.update(
                workflows,
                new BuildsScreenMsg.DefaultRead(
                        Option.none(),
                        false,
                        Option.of(new DefaultBranchPage(MAIN, new BuildsPage(ON_MAIN, 3, Option.none())))));
    }

    private static BuildsScreen screen(Next next) {
        return next.model().part(BuildsScreen.PART);
    }

    private static BuildsScreenMsg picked(BuildsScreen.Filter filter) {
        return new BuildsScreenMsg.FilterPicked(filter);
    }

    @Test
    void shownItReadsTheWorkflowsTheDefaultBranchAndTheRunsInProgress() {
        Next shown = shown(model());

        assertEquals(BuildsScreen.SCREEN, shown.model().screen());
        assertTrue(
                shown.cmds().contains(new LoadScreenWorkflows()), shown.cmds().mkString("\n"));
        assertTrue(
                shown.cmds().contains(new LoadDefaultBuilds(Option.none(), false)),
                shown.cmds().mkString("\n"));
        assertTrue(
                shown.cmds().contains(new CountRunsInProgress()), shown.cmds().mkString("\n"));
    }

    @Test
    void itOpensOnTheRuns() {
        assertEquals(BuildsScreen.RUNS_PANE, BuildsScreen.SCREEN.first());
    }

    @Test
    void hiddenATickReadsNothing() {
        assertEquals(
                List.empty(), Update.update(model(), new BuildsScreenMsg.Tick()).cmds());
        assertEquals(
                List.empty(),
                Update.update(model(), new BuildsScreenMsg.RunningTick()).cmds());
    }

    @Test
    void theWorkflowsListTheActiveOnesThoseOnTheTipOfTheDefaultBranchFirst() {
        BuildsScreen screen = screen(read(model()));

        assertEquals(List.of("Build", "Publish"), screen.listed().map(RepoWorkflow::name));
        assertEquals(Option.of(MAIN), screen.defaultName());
    }

    @Test
    void itStartsOnTheWorkflowWhoseLastBuildOnTheDefaultBranchFailedAndReadsItsRunsOnEveryBranch() {
        Next read = read(model());

        assertEquals(Option.of(BUILD), screen(read).shown());
        assertTrue(screen(read).failingOnDefault());
        // Asked for once the default branch was read, which says which workflow the screen starts on.
        assertTrue(
                read.cmds().contains(new LoadScreenRuns("W_build", Option.none(), false)),
                read.cmds().mkString());
    }

    @Test
    void untilTheDefaultBranchIsReadNoWorkflowIsChosenNorAreRunsRead() {
        Next workflows =
                Update.update(shown(model()).model(), new BuildsScreenMsg.WorkflowsRead(List.of(PUBLISH, BUILD)));

        assertEquals(Option.none(), screen(workflows).shown());
        assertTrue(screen(workflows).runs().isEmpty());
        assertTrue(screen(workflows).filtered() instanceof Loadable.Loading<?>);
    }

    @Test
    void anotherWorkflowHighlightedReadsItsRunsAndForgetsTheRunHighlighted() {
        Model highlighted = Update.update(read(model()).model(), new BuildsScreenMsg.RunHighlighted(2))
                .model();
        Next publish = Update.update(highlighted, new BuildsScreenMsg.WorkflowHighlighted("Publish"));

        assertEquals(Option.of(PUBLISH), screen(publish).shown());
        assertEquals(Option.none(), screen(publish).run());
        assertTrue(publish.cmds().contains(new LoadScreenRuns("W_publish", Option.none(), false)));
    }

    @Test
    void onEveryBranchTheRunsOfTheBranchCheckedOutAreMarked() {
        Model read = read(model()).model();
        List<Build> every = List.of(run("Build", 10, "SUCCESS", FEATURE), run("Build", 11, "SUCCESS", MAIN));
        Model runs = Update.update(
                        read,
                        new BuildsScreenMsg.RunsRead(
                                "W_build",
                                Option.none(),
                                false,
                                Option.of(new WorkflowRunsPage(every, 2, Option.none()))))
                .model();

        BuildsScreen screen = runs.part(BuildsScreen.PART);
        assertEquals(every, screen.filtered().getOrElse(List.empty()));
        assertEquals(
                io.vavr.collection.HashSet.of(new Sha("abcdef10")),
                screen.drawn(runs).mine());
    }

    @Test
    void onTheDefaultBranchItsOwnBuildsOfTheWorkflowShowWithoutReadingMore() {
        Next onMain = Update.update(read(model()).model(), picked(BuildsScreen.Filter.DEFAULT_BRANCH));

        assertEquals(
                List.of(2L, 3L),
                screen(onMain).filtered().getOrElse(List.empty()).map(Build::runId));
        assertFalse(
                onMain.cmds().exists(cmd -> cmd instanceof LoadScreenRuns),
                onMain.cmds().mkString());
    }

    @Test
    void mineReadsTheBuildsOfTheBranchCheckedOutOnGitHub() {
        Next mine = Update.update(read(model()).model(), picked(BuildsScreen.Filter.MINE));

        assertEquals(BuildsScreen.Filter.MINE, screen(mine).filter());
        assertTrue(mine.cmds().contains(new LoadBranchBuilds(ORIGIN_FEATURE, Option.none(), false)));
    }

    @Test
    void withoutABranchOnGitHubMineDoesNotApply() {
        Model detached = TestModels.localBranchesRead(
                        PullRequestFixtures.loadedWithTheFeature(),
                        List.of(Fixtures.branch(new BranchName("local"), true, new Sha("100"))))
                .model();
        Model read = read(detached).model();

        assertTrue(BuildsScreen.mineOf(read).isLeft());
        assertEquals(
                BuildsScreen.Filter.EVERY_BRANCH,
                screen(Update.update(read, picked(BuildsScreen.Filter.MINE))).filter());
    }

    @Test
    void bOnABranchShowsTheScreenFilteredToIt() {
        Next opened = Update.update(model(), new BuildsScreenMsg.Opened(FEATURE));

        assertEquals(BuildsScreen.SCREEN, opened.model().screen());
        assertEquals(BuildsScreen.Filter.MINE, screen(opened).filter());
        assertEquals(Option.of(FEATURE), screen(opened).chosen());
    }

    @Test
    void moreReadsTheNextRunsOfTheWorkflowFromWhereTheyEnd() {
        Model read = read(model()).model();
        Model runs = Update.update(
                        read,
                        new BuildsScreenMsg.RunsRead(
                                "W_build",
                                Option.none(),
                                false,
                                Option.of(new WorkflowRunsPage(
                                        List.of(run("Build", 10, "SUCCESS", FEATURE)), 100, Option.of("cursor")))))
                .model();
        Next more = Update.update(runs, new BuildsScreenMsg.MoreRequested());

        assertEquals(List.of(new LoadScreenRuns("W_build", Option.of("cursor"), false)), more.cmds());
    }

    @Test
    void readingTheNewestAgainKeepsTheOlderRunsRead() {
        Model read = read(model()).model();
        Model runs = Update.update(
                        read,
                        new BuildsScreenMsg.RunsRead(
                                "W_build",
                                Option.none(),
                                false,
                                Option.of(new WorkflowRunsPage(
                                        List.of(run("Build", 10, "SUCCESS", FEATURE)), 1, Option.none()))))
                .model();
        Model again = Update.update(
                        runs,
                        new BuildsScreenMsg.RunsRead(
                                "W_build",
                                Option.none(),
                                true,
                                Option.of(new WorkflowRunsPage(
                                        List.of(run("Build", 9, "SUCCESS", FEATURE)), 1, Option.none()))))
                .model();

        assertEquals(
                List.of(9L, 10L),
                again.part(BuildsScreen.PART).filtered().getOrElse(List.empty()).map(Build::runId));
    }

    @Test
    void aTickOnTheScreenReadsTheNewestAgain() {
        Next tick = Update.update(read(model()).model(), new BuildsScreenMsg.Tick());

        assertTrue(
                tick.cmds().contains(new LoadDefaultBuilds(Option.none(), true)),
                tick.cmds().mkString());
        assertTrue(tick.cmds().contains(new CountRunsInProgress()), tick.cmds().mkString());
    }

    @Test
    void onlyARunThatFailedRunsAgain() {
        Model read = read(model()).model();
        Build failed = run("Build", 2, "FAILURE", MAIN);
        Build passed = run("Build", 3, "SUCCESS", MAIN);

        assertEquals(
                List.<Cmd>of(new RerunRunCmd(2, "Build · Commit 2")),
                Update.update(read, new BuildsScreenMsg.RerunRequested(failed)).cmds());
        assertEquals(
                List.empty(),
                Update.update(read, new BuildsScreenMsg.RerunRequested(passed)).cmds());
    }

    @Test
    void theChartShowsAsManyAsPicked() {
        Model picked = Update.update(read(model()).model(), new BuildsScreenMsg.WindowPicked(Option.of(10)))
                .model();

        assertEquals(Option.of(10), picked.part(BuildsScreen.PART).drawn(picked).chart());
    }

    @Test
    void aClickInTheChartHighlightsItsRun() {
        Model read = read(model()).model();
        Model once = Update.update(read, new BuildsScreenMsg.RunClicked(3)).model();
        Model twice = Update.update(once, new BuildsScreenMsg.RunClicked(3)).model();

        assertEquals(Option.of(3L), twice.part(BuildsScreen.PART).run());
        assertEquals(
                Option.of(new BuildsScreen.Clicked(3, 2)),
                twice.part(BuildsScreen.PART).clicked());
    }

    @Test
    void theRepositoryScreenAgainReadsNothingMore() {
        Next back = Update.update(read(model()).model(), new ScreenMsg.Toggled(BuildsScreen.SCREEN));

        assertEquals(Screen.REPOSITORY, back.model().screen());
        assertEquals(
                List.empty(),
                Update.update(back.model(), new BuildsScreenMsg.Tick()).cmds());
    }
}
