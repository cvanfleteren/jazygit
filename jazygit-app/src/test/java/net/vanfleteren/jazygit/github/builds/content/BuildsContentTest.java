package net.vanfleteren.jazygit.github.builds.content;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.tui.event.KeyEvent;
import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Instant;
import java.util.ArrayList;
import net.vanfleteren.jazygit.content.ContentPanel;
import net.vanfleteren.jazygit.content.ContentViews;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.gh.checks.StubChecks;
import net.vanfleteren.jazygit.gh.model.Build;
import net.vanfleteren.jazygit.gh.model.BuildsPage;
import net.vanfleteren.jazygit.gh.process.GhError;
import net.vanfleteren.jazygit.git.StubGit;
import net.vanfleteren.jazygit.git.files.StubDiffs;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.Fixtures;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.RemoteName;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.Upstream;
import net.vanfleteren.jazygit.git.process.GitError;
import net.vanfleteren.jazygit.github.builds.BuildPickPopup;
import net.vanfleteren.jazygit.github.builds.BuildsMsg;
import net.vanfleteren.jazygit.github.builds.BuildsPopup;
import net.vanfleteren.jazygit.github.builds.BuildsWindowPopup;
import net.vanfleteren.jazygit.github.builds.LoadBuilds;
import net.vanfleteren.jazygit.state.AreaId;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.github.BranchChecksMsg;
import net.vanfleteren.jazygit.state.github.GitHubChecks;
import net.vanfleteren.jazygit.state.github.builds.BuildsAt;
import net.vanfleteren.jazygit.state.github.builds.BuildsViewMsg;
import net.vanfleteren.jazygit.state.github.builds.ReadWorkflows;
import net.vanfleteren.jazygit.state.load.SelectionMsg;
import net.vanfleteren.jazygit.state.view.BuildsShown;
import net.vanfleteren.jazygit.state.view.BuildsView;
import net.vanfleteren.jazygit.state.view.BuildsView.FileAt;
import net.vanfleteren.jazygit.state.view.Workflow;
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.branches.BranchesPanel;
import net.vanfleteren.jazygit.ui.content.Shown;
import net.vanfleteren.jazygit.ui.content.Tone;
import net.vanfleteren.jazygit.ui.keys.Keymap;
import net.vanfleteren.jazygit.ui.keys.Scopes;
import net.vanfleteren.jazygit.ui.text.Placeholders;
import net.vanfleteren.jazygit.ui.theme.Theme;
import org.junit.jupiter.api.Test;

/**
 * Showing the builds of a local branch on GitHub in place of its log, read some commits at a time (ADR 131).
 */
public class BuildsContentTest {

    private static final BranchName MAIN = new BranchName("main");
    private static final BranchName LOCAL = new BranchName("local");
    private static final RemoteBranch ORIGIN_MAIN = new RemoteBranch("origin", MAIN);
    private static final Build BUILD = new Build(
            "Build",
            "push",
            1,
            1,
            "abcdef0",
            "A commit",
            Instant.parse("2026-11-14T23:00:00Z"),
            List.of(new Build.Job(
                    "verify",
                    "SUCCESS",
                    Option.of(Instant.parse("2026-11-14T23:00:05Z")),
                    Option.of(Instant.parse("2026-11-14T23:04:00Z")))));

    private static Branch branch(BranchName name, Option<Upstream> upstream) {
        return Fixtures.branch(name, false, new Sha("abc"), Instant.EPOCH, true, false, upstream);
    }

    /**
     * The sample model with {@code main}, which follows {@code origin/main}, and {@code local}, which follows
     * nothing; {@code main} highlighted in the branches pane, its log read.
     */
    private static Model model() {
        Model read = TestModels.localBranchesRead(
                        TestModels.loaded(),
                        List.of(
                                branch(MAIN, Option.of(new Upstream(new BranchRef.Remote(ORIGIN_MAIN), 0, 0))),
                                branch(LOCAL, Option.none())))
                .model();
        BranchRef main = new BranchRef.Local(MAIN);
        return Update.update(
                        Update.update(read, new SelectionMsg.BranchSelected(main))
                                .model(),
                        TestModels.branchLogLoaded(main, List.empty()))
                .model();
    }

    private static BuildsPage page(int commits, Option<String> next) {
        return new BuildsPage(List.of(BUILD), commits, next);
    }

    private static Shown content(Model model) {
        return ContentViews.of(model, BranchesPanel.PANE);
    }

    @Test
    void bOnABranchReadsTheBuildsOfTheBranchItFollowsFromItsTip() {
        Next asked = Update.update(model(), new BuildsMsg.Toggled(MAIN));

        assertEquals(
                Option.of(BuildsView.reading(MAIN, ORIGIN_MAIN)),
                asked.model().repoView().branches().builds());
        assertEquals(List.of(new LoadBuilds(ORIGIN_MAIN, Option.none())), asked.cmds());
        assertEquals("Builds: main · reading, 0 commits so far", ((Shown.Message) content(asked.model())).title());
    }

    @Test
    void bAgainShowsTheLogAgain() {
        Model shown = Update.update(model(), new BuildsMsg.Toggled(MAIN)).model();

        Next again = Update.update(shown, new BuildsMsg.Toggled(MAIN));

        assertEquals(Option.none(), again.model().repoView().branches().builds());
        assertEquals(AreaId.LOG, ((Shown.Area) content(again.model())).area().id());
    }

    /**
     * The builds take the focus from the branches for the branch B opened them for, and give it back once B closes
     * them (ADR 155).
     */
    @Test
    void theAreaOfTheBuildsHasOpenTheBranchBOpenedThemFor() {
        Model shown = Update.update(model(), new BuildsMsg.Toggled(MAIN)).model();
        Model closed = Update.update(shown, new BuildsMsg.Toggled(MAIN)).model();

        assertEquals(Option.none(), BuildsSource.AREA.opened(model()));
        assertEquals(Option.of(MAIN), BuildsSource.AREA.opened(shown));
        assertEquals(Option.none(), BuildsSource.AREA.opened(closed));
    }

    @Test
    void theFirstCommitsShowWhileTheNextAreRead() {
        Model shown = Update.update(model(), new BuildsMsg.Toggled(MAIN)).model();

        Next read = Update.update(
                shown, new BuildsMsg.Read(ORIGIN_MAIN, Option.none(), Option.of(page(25, Option.of("c25")))));

        assertEquals(List.of(new LoadBuilds(ORIGIN_MAIN, Option.of("c25"))), read.cmds());
        Shown.Area area = (Shown.Area) content(read.model());
        assertEquals(BuildsSource.AREA, area.area());
        assertEquals("Builds: main · reading, 25 commits so far", area.title());
        BuildsShown drawn = ((BuildsBody) area.body()).shown();
        assertEquals(new BuildsView.Pipeline("Build", "push"), drawn.shown());
        assertEquals(new Loadable.Loaded<>(List.of(BUILD)), drawn.builds());
        assertEquals(Option.none(), drawn.baseline());
    }

    @Test
    void itStopsReadingAtAHundredCommits() {
        Model reading = Update.update(model(), new BuildsMsg.Toggled(MAIN)).model();
        Option<String> after = Option.none();
        for (int page = 1; page <= 4; page++) {
            Next read = Update.update(
                    reading, new BuildsMsg.Read(ORIGIN_MAIN, after, Option.of(page(25, Option.of("c" + page * 25)))));
            reading = read.model();
            after = Option.of("c" + page * 25);
            assertEquals(page < 4, !read.cmds().isEmpty());
        }

        BuildsView view = reading.repoView().branches().builds().get();
        assertFalse(view.reading());
        assertEquals(100, view.commits());
        assertEquals(4, view.builds().getOrElse(List.empty()).size());
        assertEquals("Builds: main · last 100 commits · m for more", ((Shown.Area) content(reading)).title());
    }

    @Test
    void mReadsAHundredMoreCommitsFromWhereThoseReadEnd() {
        Model read = Update.update(
                        Update.update(model(), new BuildsMsg.Toggled(MAIN)).model(),
                        new BuildsMsg.Read(ORIGIN_MAIN, Option.none(), Option.of(page(100, Option.of("c100")))))
                .model();

        Next more = Update.update(read, new BuildsMsg.MoreRequested());

        assertEquals(List.of(new LoadBuilds(ORIGIN_MAIN, Option.of("c100"))), more.cmds());
        assertEquals("Builds: main · reading, 100 commits so far", ((Shown.Area) content(more.model())).title());
        assertTrue(Update.update(more.model(), new BuildsMsg.MoreRequested())
                .cmds()
                .isEmpty());

        Next next = Update.update(
                more.model(),
                new BuildsMsg.Read(ORIGIN_MAIN, Option.of("c100"), Option.of(page(75, Option.of("c175")))));
        assertEquals(List.of(new LoadBuilds(ORIGIN_MAIN, Option.of("c175"))), next.cmds());
        Model done = Update.update(
                        next.model(),
                        new BuildsMsg.Read(ORIGIN_MAIN, Option.of("c175"), Option.of(page(25, Option.of("c200")))))
                .model();
        BuildsView view = done.repoView().branches().builds().get();
        assertFalse(view.reading());
        assertEquals(200, view.commits());
        assertEquals(3, view.builds().getOrElse(List.empty()).size());
        assertEquals("Builds: main · last 200 commits · m for more", ((Shown.Area) content(done)).title());
    }

    @Test
    void mReadsNothingAtTheOldestCommit() {
        Model read = Update.update(
                        Update.update(model(), new BuildsMsg.Toggled(MAIN)).model(),
                        new BuildsMsg.Read(ORIGIN_MAIN, Option.none(), Option.of(page(3, Option.none()))))
                .model();

        Next more = Update.update(read, new BuildsMsg.MoreRequested());

        assertSame(read, more.model());
        assertTrue(more.cmds().isEmpty());
        assertEquals("Builds: main · last 3 commits", ((Shown.Area) content(read)).title());
    }

    @Test
    void mReadsAgainFromWhereReadingFailed() {
        Model read = Update.update(
                        Update.update(model(), new BuildsMsg.Toggled(MAIN)).model(),
                        new BuildsMsg.Read(ORIGIN_MAIN, Option.none(), Option.of(page(25, Option.of("c25")))))
                .model();
        Model stopped = Update.update(
                        read,
                        new BuildsMsg.Failed(
                                ORIGIN_MAIN, Option.of("c25"), Failure.of(new GhError.UnexpectedOutput("timeout"))))
                .model();

        Next again = Update.update(stopped, new BuildsMsg.MoreRequested());

        assertEquals(
                "Builds: main · 25 commits, could not read more · m to try again",
                ((Shown.Area) content(stopped)).title());
        assertEquals(List.of(new LoadBuilds(ORIGIN_MAIN, Option.of("c25"))), again.cmds());
        BuildsView view = again.model().repoView().branches().builds().get();
        assertTrue(view.reading());
        assertEquals(Option.none(), view.stopped());
        assertEquals(125, view.limit());
    }

    @Test
    void itStopsAtTheOldestCommit() {
        Model shown = Update.update(model(), new BuildsMsg.Toggled(MAIN)).model();

        Next read =
                Update.update(shown, new BuildsMsg.Read(ORIGIN_MAIN, Option.none(), Option.of(page(3, Option.none()))));

        assertTrue(read.cmds().isEmpty());
        assertFalse(read.model().repoView().branches().builds().get().reading());
    }

    @Test
    void commitsNoLongerAwaitedAreLeftOut() {
        Model shown = Update.update(model(), new BuildsMsg.Toggled(MAIN)).model();

        Next stale = Update.update(
                shown, new BuildsMsg.Read(ORIGIN_MAIN, Option.of("c50"), Option.of(page(25, Option.none()))));

        assertSame(shown, stale.model());
        assertTrue(stale.cmds().isEmpty());
    }

    @Test
    void aBranchNotOnGitHubHasNoBuilds() {
        Model shown = Update.update(model(), new BuildsMsg.Toggled(MAIN)).model();

        Model read = Update.update(shown, new BuildsMsg.Read(ORIGIN_MAIN, Option.none(), Option.none()))
                .model();

        assertEquals(
                new Shown.Message("Builds: main · last 0 commits", "origin/main is not on GitHub", Tone.DIM),
                content(read));
    }

    @Test
    void commitsWithoutBuildsSayHowManyWereRead() {
        Model shown = Update.update(model(), new BuildsMsg.Toggled(MAIN)).model();

        Model read = Update.update(
                        shown,
                        new BuildsMsg.Read(
                                ORIGIN_MAIN, Option.none(), Option.of(new BuildsPage(List.empty(), 25, Option.none()))))
                .model();

        assertEquals(
                new Shown.Message(
                        "Builds: main · last 25 commits",
                        "No builds on the last 25 commits of origin/main on GitHub",
                        Tone.DIM),
                content(read));
    }

    @Test
    void commitsWithoutBuildsWhileMoreAreReadShowLoading() {
        Model shown = Update.update(model(), new BuildsMsg.Toggled(MAIN)).model();

        Model read = Update.update(
                        shown,
                        new BuildsMsg.Read(
                                ORIGIN_MAIN,
                                Option.none(),
                                Option.of(new BuildsPage(List.empty(), 25, Option.of("c25")))))
                .model();

        assertEquals(
                new Shown.Message("Builds: main · reading, 25 commits so far", Placeholders.loading(), Tone.DIM),
                content(read));
    }

    @Test
    void aBranchThatFollowsNoneSaysSoWithoutReading() {
        Model local = Update.update(model(), new SelectionMsg.BranchSelected(new BranchRef.Local(LOCAL)))
                .model();
        local = Update.update(local, TestModels.branchLogLoaded(new BranchRef.Local(LOCAL), List.empty()))
                .model();

        Next asked = Update.update(local, new BuildsMsg.Toggled(LOCAL));

        assertTrue(asked.cmds().isEmpty());
        assertEquals(
                "local follows no branch on GitHub, so it has no builds to show",
                ((Shown.Message) content(asked.model())).text());
    }

    @Test
    void aFailedFirstReadShowsWhy() {
        Model shown = Update.update(model(), new BuildsMsg.Toggled(MAIN)).model();

        Model failed = Update.update(
                        shown,
                        new BuildsMsg.Failed(
                                ORIGIN_MAIN, Option.none(), Failure.of(new GhError.UnexpectedOutput("timeout"))))
                .model();

        assertInstanceOf(
                Loadable.Failed.class,
                failed.repoView().branches().builds().get().builds());
        assertEquals(Tone.ERROR, ((Shown.Message) content(failed)).tone());
    }

    @Test
    void mReadsAgainFromTheTipWhenTheFirstReadFailed() {
        Model failed = Update.update(
                        Update.update(model(), new BuildsMsg.Toggled(MAIN)).model(),
                        new BuildsMsg.Failed(
                                ORIGIN_MAIN, Option.none(), Failure.of(new GhError.UnexpectedOutput("timeout"))))
                .model();
        assertEquals("Builds: main · last 0 commits · m to try again", ((Shown.Message) content(failed)).title());

        Next again = Update.update(failed, new BuildsMsg.MoreRequested());

        assertEquals(List.of(new LoadBuilds(ORIGIN_MAIN, Option.none())), again.cmds());
        BuildsView view = again.model().repoView().branches().builds().get();
        assertTrue(view.reading());
        assertInstanceOf(Loadable.Loading.class, view.builds());
        assertTrue(Update.update(again.model(), new BuildsMsg.MoreRequested())
                .cmds()
                .isEmpty());
        Model read = Update.update(
                        again.model(),
                        new BuildsMsg.Read(ORIGIN_MAIN, Option.none(), Option.of(page(3, Option.none()))))
                .model();
        assertEquals(
                new Loadable.Loaded<>(List.of(BUILD)),
                read.repoView().branches().builds().get().builds());
    }

    @Test
    void aFailedLaterReadKeepsWhatWasRead() {
        Model shown = Update.update(model(), new BuildsMsg.Toggled(MAIN)).model();
        Model first = Update.update(
                        shown, new BuildsMsg.Read(ORIGIN_MAIN, Option.none(), Option.of(page(25, Option.of("c25")))))
                .model();

        Next failed = Update.update(
                first,
                new BuildsMsg.Failed(
                        ORIGIN_MAIN, Option.of("c25"), Failure.of(new GhError.UnexpectedOutput("timeout"))));

        assertTrue(failed.cmds().isEmpty());
        assertEquals(
                List.of(BUILD),
                failed.model().repoView().branches().builds().get().builds().getOrElse(List.empty()));
        assertEquals(
                "Builds: main · 25 commits, could not read more · m to try again",
                ((Shown.Area) content(failed.model())).title());
    }

    @Test
    void anotherBranchHighlightedShowsItsLog() {
        Model shown = Update.update(model(), new BuildsMsg.Toggled(MAIN)).model();
        shown = Update.update(shown, new BuildsMsg.Read(ORIGIN_MAIN, Option.none(), Option.of(page(3, Option.none()))))
                .model();

        Model other = Update.update(shown, new SelectionMsg.BranchSelected(new BranchRef.Local(LOCAL)))
                .model();
        other = Update.update(other, TestModels.branchLogLoaded(new BranchRef.Local(LOCAL), List.empty()))
                .model();

        assertEquals(AreaId.LOG, ((Shown.Area) content(other)).area().id());
    }

    @Test
    void aBranchOnARemoteNotOnGitHubSaysSoWithoutReading() {
        RemoteBranch elsewhere = new RemoteBranch("gitlab", LOCAL);
        Model read = TestModels.localBranchesRead(
                        model(),
                        List.of(
                                branch(MAIN, Option.of(new Upstream(new BranchRef.Remote(ORIGIN_MAIN), 0, 0))),
                                branch(LOCAL, Option.of(new Upstream(new BranchRef.Remote(elsewhere), 0, 0)))))
                .model();
        Model known = Update.update(
                        read,
                        new BranchChecksMsg.Loaded(
                                new GitHubChecks(HashSet.of(new RemoteName("origin")), HashMap.empty())))
                .model();
        Model local = Update.update(known, new SelectionMsg.BranchSelected(new BranchRef.Local(LOCAL)))
                .model();
        local = Update.update(local, TestModels.branchLogLoaded(new BranchRef.Local(LOCAL), List.empty()))
                .model();

        Next asked = Update.update(local, new BuildsMsg.Toggled(LOCAL));

        assertTrue(asked.cmds().isEmpty());
        assertEquals(
                "local follows no branch on GitHub, so it has no builds to show",
                ((Shown.Message) content(asked.model())).text());
    }

    @Test
    void theChartIsDrawnAnewForTheWidthItGets() throws Exception {
        List<Build> builds = List.range(0, 150)
                .map(index -> new Build(
                        "Build",
                        "push",
                        index,
                        1,
                        "abcdef" + index,
                        "Commit " + index,
                        Instant.parse("2026-11-14T00:00:00Z").plusSeconds(600L * index),
                        BUILD.jobs()
                                .map(job -> new Build.Job(
                                        job.name(),
                                        job.conclusion(),
                                        job.startedAt().map(at -> at.plusSeconds(600L * index - 82800)),
                                        job.completedAt().map(at -> at.plusSeconds(600L * index - 82800))))))
                .reverse();
        Model shown = Update.update(model(), new BuildsMsg.Toggled(MAIN)).model();
        Model read = Update.update(
                        shown,
                        new BuildsMsg.Read(
                                ORIGIN_MAIN, Option.none(), Option.of(new BuildsPage(builds, 25, Option.none()))))
                .model();
        ContentPanel panel = new ContentPanel(Theme.SpanStyles.NONE, () -> false);

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(read, BranchesPanel.PANE))) {
            int narrow = bars(RenderedText.ofSize(testRunner, () -> panel.render(read, BranchesPanel.PANE), 60, 40));
            int wide = bars(RenderedText.ofSize(testRunner, () -> panel.render(read, BranchesPanel.PANE), 120, 40));
            int again = bars(RenderedText.ofSize(testRunner, () -> panel.render(read, BranchesPanel.PANE), 60, 40));
            assertTrue(narrow > 0 && narrow < 60, String.valueOf(narrow));
            assertTrue(wide > narrow + 40, "the chart follows the width of the area: " + narrow + " then " + wide);
            assertEquals(narrow, again);
        }
    }

    @Test
    void theFileOfTheWorkflowOfTheLastBuildIsReadAtItsCommitOnce() {
        Build withFile = withFile(BUILD);
        Model shown = Update.update(model(), new BuildsMsg.Toggled(MAIN)).model();

        Next read = Update.update(
                shown,
                new BuildsMsg.Read(
                        ORIGIN_MAIN,
                        Option.none(),
                        Option.of(new BuildsPage(List.of(withFile), 25, Option.of("c25")))));
        Next again = Update.update(
                read.model(),
                new BuildsMsg.Read(
                        ORIGIN_MAIN, Option.of("c25"), Option.of(new BuildsPage(List.empty(), 25, Option.none()))));

        FileAt publish = new FileAt("abcdef0", ".github/workflows/publish.yml");
        assertEquals(List.of(new ReadWorkflows(publish), new LoadBuilds(ORIGIN_MAIN, Option.of("c25"))), read.cmds());
        assertTrue(again.cmds().isEmpty());
    }

    @Test
    void theWorkflowReadSaysWhichJobOfTheLastBuildNeedsWhich() {
        Build withFile = withFile(new Build(
                "Build",
                "push",
                1,
                1,
                "abcdef0",
                "A commit",
                Instant.parse("2026-11-14T23:00:00Z"),
                List.of(
                        BUILD.jobs().head(),
                        new Build.Job(
                                "package",
                                "SUCCESS",
                                Option.of(Instant.parse("2026-11-14T23:04:01Z")),
                                Option.of(Instant.parse("2026-11-14T23:05:00Z"))))));
        Model shown = Update.update(model(), new BuildsMsg.Toggled(MAIN)).model();
        Model read = Update.update(
                        shown,
                        new BuildsMsg.Read(
                                ORIGIN_MAIN,
                                Option.none(),
                                Option.of(new BuildsPage(List.of(withFile), 3, Option.none()))))
                .model();
        FileAt publish = new FileAt("abcdef0", ".github/workflows/publish.yml");

        Model known = Update.update(
                        read,
                        new BuildsViewMsg.WorkflowsRead(HashMap.of(
                                publish, Workflow.parse("jobs:\n  verify: {}\n  package:\n    needs: verify\n"))))
                .model();

        assertEquals(
                HashMap.of(1L, HashMap.of("verify", HashSet.<String>empty(), "package", HashSet.of("verify"))),
                ((BuildsBody) ((Shown.Area) content(known)).body()).shown().needs());
    }

    @Test
    void readingTheWorkflowsReadsTheFileAndThoseItCallsAtTheCommit() {
        String sha = "abcdef0123456789abcdef0123456789abcdef01";
        Env env = new Env(
                StubGit.of().withDiffs(new StubDiffs() {
                    @Override
                    public Either<GitError, String> treeText(Sha at, String path) {
                        return at.value().equals(sha) && path.equals(".github/workflows/publish.yml")
                                ? Either.right("jobs:\n  verify:\n    uses: ./.github/workflows/build.yml\n")
                                : at.value().equals(sha) && path.equals(".github/workflows/build.yml")
                                        ? Either.right("jobs:\n  test: {}\n")
                                        : Either.left(new GitError.Unreadable(path, "not there"));
                    }
                }),
                StubGitHub.of());

        BuildsViewMsg.WorkflowsRead read = (BuildsViewMsg.WorkflowsRead)
                new ReadWorkflows(new FileAt(sha, ".github/workflows/publish.yml")).run(env);

        assertEquals(2, read.workflows().size());
        assertEquals(
                List.of("test"),
                read.workflows()
                        .get(new FileAt(sha, ".github/workflows/build.yml"))
                        .get()
                        .get()
                        .jobs()
                        .map(Workflow.Job::key));
    }

    @Test
    void aWorkflowGitDoesNotHaveIsReadFromGitHubAtTheCommit() {
        // The head commit of a pull request merged and deleted since is not fetched; GitHub still has it (ADR 138).
        String sha = "abcdef0123456789abcdef0123456789abcdef01";
        ArrayList<String> asked = new ArrayList<>();
        Env env = new Env(StubGit.of(), StubGitHub.of().withChecks(new StubChecks() {
            @Override
            public Either<GhError, String> workflowFile(String at, String path) {
                asked.add(path);
                return at.equals(sha) && path.equals(".github/workflows/publish.yml")
                        ? Either.right("jobs:\n  verify:\n    uses: ./.github/workflows/build.yml\n")
                        : at.equals(sha) && path.equals(".github/workflows/build.yml")
                                ? Either.right("jobs:\n  test: {}\n")
                                : StubChecks.super.workflowFile(at, path);
            }
        }));

        BuildsViewMsg.WorkflowsRead read = (BuildsViewMsg.WorkflowsRead)
                new ReadWorkflows(new FileAt(sha, ".github/workflows/publish.yml")).run(env);

        assertEquals(
                List.of("test"),
                read.workflows()
                        .get(new FileAt(sha, ".github/workflows/build.yml"))
                        .get()
                        .get()
                        .jobs()
                        .map(Workflow.Job::key));
        assertEquals(List.of(".github/workflows/publish.yml", ".github/workflows/build.yml"), List.ofAll(asked));
    }

    @Test
    void aWorkflowGitHasIsNotAskedOfGitHub() {
        String sha = "abcdef0123456789abcdef0123456789abcdef01";
        Env env = new Env(
                StubGit.of().withDiffs(new StubDiffs() {
                    @Override
                    public Either<GitError, String> treeText(Sha at, String path) {
                        return Either.right("jobs:\n  test: {}\n");
                    }
                }),
                StubGitHub.of().withChecks(new StubChecks() {
                    @Override
                    public Either<GhError, String> workflowFile(String at, String path) {
                        throw new AssertionError("asked GitHub for " + path);
                    }
                }));

        BuildsViewMsg.WorkflowsRead read = (BuildsViewMsg.WorkflowsRead)
                new ReadWorkflows(new FileAt(sha, ".github/workflows/build.yml")).run(env);

        assertTrue(read.workflows()
                .get(new FileAt(sha, ".github/workflows/build.yml"))
                .get()
                .isDefined());
    }

    @Test
    void aWorkflowThatCannotBeReadIsNone() {
        Env env = new Env(StubGit.of(), StubGitHub.of());
        FileAt file = new FileAt("abcdef0123456789abcdef0123456789abcdef01", ".github/workflows/gone.yml");

        assertEquals(
                new BuildsViewMsg.WorkflowsRead(HashMap.of(file, Option.none())), new ReadWorkflows(file).run(env));
    }

    @Test
    void aWorkflowGitHubFailsToGiveIsNoneTillTheBuildsShowAgain() {
        // Read once a view, as the builds and the runs are: not asked again on every update while GitHub cannot be
        // reached (ADR 138).
        Env env = new Env(StubGit.of(), StubGitHub.of().withChecks(new StubChecks() {
            @Override
            public Either<GhError, String> workflowFile(String at, String path) {
                return Either.left(new GhError.TimedOut("api"));
            }
        }));
        FileAt publish = new FileAt("abcdef0", ".github/workflows/publish.yml");
        Model shown = Update.update(model(), new BuildsMsg.Toggled(MAIN)).model();
        Next read = Update.update(
                shown,
                new BuildsMsg.Read(
                        ORIGIN_MAIN,
                        Option.none(),
                        Option.of(new BuildsPage(List.of(withFile(BUILD)), 25, Option.of("c25")))));

        Msg failed = new ReadWorkflows(publish).run(env);
        Next after = Update.update(read.model(), failed);
        Next again = Update.update(
                after.model(),
                new BuildsMsg.Read(
                        ORIGIN_MAIN, Option.of("c25"), Option.of(new BuildsPage(List.empty(), 25, Option.none()))));

        assertEquals(new BuildsViewMsg.WorkflowsRead(HashMap.of(publish, Option.none())), failed);
        assertTrue(after.cmds()
                .appendAll(again.cmds())
                .filter(ReadWorkflows.class::isInstance)
                .isEmpty());
    }

    @Test
    void bracketsStepThroughTheWorkflowsAndWChoosesOne() {
        Keymap keys = Keymap.standard();

        assertEquals(
                Option.of(new BuildsMsg.Stepped(1)), keys.handle(Scopes.BUILDS, KeyEvent.ofChar(']'), Option.none()));
        assertEquals(
                Option.of(new BuildsMsg.Stepped(-1)), keys.handle(Scopes.BUILDS, KeyEvent.ofChar('['), Option.none()));
        assertEquals(
                Option.of(new BuildsMsg.PickRequested()),
                keys.handle(Scopes.BUILDS, KeyEvent.ofChar('w'), Option.none()));
        assertEquals(
                Option.of(new BuildsMsg.Closed()), keys.handle(Scopes.BUILDS, KeyEvent.ofChar('B'), Option.none()));
        assertEquals(
                Option.of(new BuildsMsg.AllToggled()), keys.handle(Scopes.BUILDS, KeyEvent.ofChar('a'), Option.none()));
        assertEquals(
                Option.of(new BuildsMsg.WindowRequested()),
                keys.handle(Scopes.BUILDS, KeyEvent.ofChar('c'), Option.none()));
        assertEquals(
                Option.of(new BuildsMsg.BuildStepped(1)),
                keys.handle(Scopes.BUILDS, KeyEvent.ofChar('n'), Option.none()));
        assertEquals(
                Option.of(new BuildsMsg.BuildStepped(-1)),
                keys.handle(Scopes.BUILDS, KeyEvent.ofChar('N'), Option.none()));
    }

    @Test
    void aTabClickedShowsItsWorkflowAndBInTheBuildsShowsTheLog() {
        Model shown = Update.update(model(), new BuildsMsg.Toggled(MAIN)).model();
        BuildsView.Pipeline lint = new BuildsView.Pipeline("Markdown", "pull_request");

        Model clicked = Update.update(shown, new BuildsViewMsg.TabClicked(lint)).model();
        Model closed = Update.update(clicked, new BuildsMsg.Closed()).model();

        assertEquals(
                Option.of(lint), clicked.repoView().branches().builds().get().selected());
        assertEquals(Option.none(), closed.repoView().branches().builds());
    }

    @Test
    void aWorkflowChosenInThePopupShowsAndStaysForAnotherBranch() {
        Build markdown =
                new Build("Markdown", "pull_request", 2, 1, "abcdef0", "A commit", BUILD.createdAt(), BUILD.jobs());
        Model shown = Update.update(model(), new BuildsMsg.Toggled(MAIN)).model();
        Model read = Update.update(
                        shown,
                        new BuildsMsg.Read(
                                ORIGIN_MAIN,
                                Option.none(),
                                Option.of(new BuildsPage(List.of(BUILD, markdown), 3, Option.none()))))
                .model();
        BuildsView.Pipeline build = new BuildsView.Pipeline("Build", "push");
        BuildsView.Pipeline lint = new BuildsView.Pipeline("Markdown", "pull_request");
        assertEquals(
                build,
                ((BuildsBody) ((Shown.Area) content(read)).body()).shown().shown());

        Model asked = Update.update(read, new BuildsMsg.PickRequested()).model();
        assertEquals(
                List.of(build, lint),
                asked.popup(BuildsPopup.class).get().choices().map(BuildsPopup.Choice::pipeline));

        Model picked = Update.update(asked, new BuildsMsg.Picked(lint)).model();
        assertTrue(picked.popup(BuildsPopup.class).isEmpty());
        assertEquals(
                lint,
                ((BuildsBody) ((Shown.Area) content(picked)).body()).shown().shown());
        assertEquals(
                build,
                ((BuildsBody) ((Shown.Area) content(Update.update(picked, new BuildsMsg.Stepped(1))
                                        .model()))
                                .body())
                        .shown()
                        .shown());

        Model again = Update.update(picked, new BuildsMsg.Toggled(LOCAL)).model();
        assertEquals(Option.of(lint), again.repoView().branches().builds().get().selected());
    }

    @Test
    void cAsksHowManyBuildsShowAndTheChoiceStaysForAnotherBranch() {
        Model shown = Update.update(model(), new BuildsMsg.Toggled(MAIN)).model();
        Model read = Update.update(
                        shown,
                        new BuildsMsg.Read(
                                ORIGIN_MAIN,
                                Option.none(),
                                Option.of(new BuildsPage(List.of(BUILD, BUILD, BUILD), 3, Option.none()))))
                .model();

        Model asked = Update.update(read, new BuildsMsg.WindowRequested()).model();
        assertEquals(Option.of(new BuildsWindowPopup(Option.none())), asked.popup(BuildsWindowPopup.class));

        Model picked =
                Update.update(asked, new BuildsMsg.WindowPicked(Option.of(2))).model();
        BuildsShown drawn = ((BuildsBody) ((Shown.Area) content(picked)).body()).shown();
        assertTrue(picked.popup(BuildsWindowPopup.class).isEmpty());
        assertEquals(2, drawn.builds().getOrElse(List.empty()).size());
        assertEquals(3, drawn.of());

        Model other = Update.update(picked, new BuildsMsg.Toggled(LOCAL)).model();
        assertEquals(Option.of(2), other.repoView().branches().builds().get().window());
    }

    @Test
    void aBuildPickedThatDoesNotShowLeavesTheLastBuildMarkedInThePopup() {
        Build before = new Build(
                "Build", "push", 2, 1, "1234567", "Before", BUILD.createdAt().minusSeconds(3600), BUILD.jobs());
        Build lint =
                new Build("Markdown", "pull_request", 3, 1, "abcdef0", "A commit", BUILD.createdAt(), BUILD.jobs());
        Model read = Update.update(
                        Update.update(model(), new BuildsMsg.Toggled(MAIN)).model(),
                        new BuildsMsg.Read(
                                ORIGIN_MAIN,
                                Option.none(),
                                Option.of(new BuildsPage(List.of(BUILD, lint, before), 2, Option.none()))))
                .model();
        Model picked = Update.update(
                        Update.update(read, new BuildsViewMsg.TabClicked(new BuildsView.Pipeline("Build", "push")))
                                .model(),
                        new BuildsViewMsg.BuildClicked(2))
                .model();
        assertEquals(
                Option.of(new BuildPickPopup(List.of(BUILD, before), Option.of(2L))),
                Update.update(picked, new BuildsMsg.BuildPickRequested())
                        .model()
                        .popup(BuildPickPopup.class));

        // On another tab, the time line shows its last build: the popup marks that.
        Model otherTab = Update.update(
                        picked, new BuildsViewMsg.TabClicked(new BuildsView.Pipeline("Markdown", "pull_request")))
                .model();
        assertEquals(
                Option.of(new BuildPickPopup(List.of(lint), Option.none())),
                Update.update(otherTab, new BuildsMsg.BuildPickRequested())
                        .model()
                        .popup(BuildPickPopup.class));

        // Nor once fewer builds show than the one picked.
        Model fewer =
                Update.update(picked, new BuildsMsg.WindowPicked(Option.of(1))).model();
        assertEquals(
                Option.of(new BuildPickPopup(List.of(BUILD), Option.none())),
                Update.update(fewer, new BuildsMsg.BuildPickRequested()).model().popup(BuildPickPopup.class));
        assertEquals(Option.of(2L), fewer.repoView().branches().builds().get().picked());
    }

    @Test
    void pPicksTheBuildWhoseJobsShowOnTheTimeLineReadingItsWorkflowAtItsCommit() {
        String older = "1234567".repeat(5) + "12345";
        Build tip = withFile(BUILD);
        Build before = withFile(new Build(
                "Build", "push", 2, 1, older, "Before", BUILD.createdAt().minusSeconds(3600), BUILD.jobs()));
        Model read = Update.update(
                        Update.update(model(), new BuildsMsg.Toggled(MAIN)).model(),
                        new BuildsMsg.Read(
                                ORIGIN_MAIN,
                                Option.none(),
                                Option.of(new BuildsPage(List.of(tip, before), 2, Option.none()))))
                .model();

        Model asked = Update.update(read, new BuildsMsg.BuildPickRequested()).model();
        assertEquals(
                Option.of(new BuildPickPopup(List.of(tip, before), Option.none())), asked.popup(BuildPickPopup.class));

        Next picked = Update.update(asked, new BuildsMsg.BuildPicked(Option.of(2L)));
        assertTrue(picked.model().popup(BuildPickPopup.class).isEmpty());
        assertEquals(List.of(new ReadWorkflows(new FileAt(older, ".github/workflows/publish.yml"))), picked.cmds());
        BuildsShown drawn = ((BuildsBody) ((Shown.Area) content(picked.model())).body()).shown();
        assertEquals(Option.of(2L), drawn.picked());

        Model again =
                Update.update(picked.model(), new BuildsViewMsg.BuildClicked(2)).model();
        assertEquals(Option.none(), again.repoView().branches().builds().get().picked());
        Model clicked = Update.update(again, new BuildsViewMsg.BuildClicked(1)).model();
        assertEquals(Option.of(1L), clicked.repoView().branches().builds().get().picked());
    }

    @Test
    void nAndNStepThroughTheBuildsThatShowFromTheOneOnTheTimeLine() {
        Model read = steppable();

        // From the last build that is done, not the newest, which runs.
        Model next = Update.update(read, new BuildsMsg.BuildStepped(1)).model();
        assertEquals(Option.of(3L), picked(next));
        // It stops at the oldest.
        assertEquals(
                Option.of(3L),
                picked(Update.update(next, new BuildsMsg.BuildStepped(1)).model()));
        Model back = Update.update(next, new BuildsMsg.BuildStepped(-1)).model();
        assertEquals(Option.of(2L), picked(back));
        Model newest = Update.update(back, new BuildsMsg.BuildStepped(-1)).model();
        assertEquals(Option.of(1L), picked(newest));
        // And at the newest.
        assertEquals(
                Option.of(1L),
                picked(Update.update(newest, new BuildsMsg.BuildStepped(-1)).model()));
    }

    @Test
    void aPickThatNoLongerShowsIsSteppedFromTheLastBuildDoneThatShows() {
        Model oldest =
                Update.update(steppable(), new BuildsViewMsg.BuildClicked(3)).model();
        Model fewer =
                Update.update(oldest, new BuildsMsg.WindowPicked(Option.of(2))).model();

        // Build 3 is still picked, but no longer shows: the time line shows build 2, the last done of those that do.
        assertEquals(
                Option.of(1L),
                picked(Update.update(fewer, new BuildsMsg.BuildStepped(-1)).model()));
        assertEquals(
                Option.of(2L),
                picked(Update.update(fewer, new BuildsMsg.BuildStepped(1)).model()));
    }

    @Test
    void withNoBuildDoneNAndNPickTheNewest() {
        Build running = new Build("Build", "push", 1, 1, "abcdef0", "Running", BUILD.createdAt(), List.empty());
        Build queued = new Build(
                "Build", "push", 2, 1, "1234567", "Queued", BUILD.createdAt().minusSeconds(60), List.empty());
        Model read = buildsRead(List.of(running, queued));

        assertEquals(
                Option.of(1L),
                picked(Update.update(read, new BuildsMsg.BuildStepped(1)).model()));
        assertEquals(
                Option.of(1L),
                picked(Update.update(read, new BuildsMsg.BuildStepped(-1)).model()));
    }

    @Test
    void withoutBuildsNAndNChangeNothing() {
        Model reading = Update.update(model(), new BuildsMsg.Toggled(MAIN)).model();
        assertEquals(
                reading, Update.update(reading, new BuildsMsg.BuildStepped(1)).model());

        Model none = buildsRead(List.empty());
        assertEquals(none, Update.update(none, new BuildsMsg.BuildStepped(1)).model());
    }

    @Test
    void whileASearchIsKeptNAndNDoNotStepTheBuilds() {
        Keymap keys = Keymap.standard();
        Option<BuildsAt> searching = Option.of(new BuildsAt(true));

        assertEquals(Option.none(), keys.handle(Scopes.BUILDS, KeyEvent.ofChar('n'), searching));
        assertEquals(Option.none(), keys.handle(Scopes.BUILDS, KeyEvent.ofChar('N'), searching));
        assertEquals(
                Option.of(new BuildsMsg.BuildStepped(1)),
                keys.handle(Scopes.BUILDS, KeyEvent.ofChar('n'), Option.of(new BuildsAt(false))));
    }

    /**
     * The builds of the main branch, read: one running, then two done, titled "Running", "Done" and "Older".
     */
    public static Model steppable() {
        Build tip = withFile(new Build("Build", "push", 1, 1, "abcdef0", "Running", BUILD.createdAt(), List.empty()));
        Build done = withFile(new Build(
                "Build", "push", 2, 1, "1234567", "Done", BUILD.createdAt().minusSeconds(60), BUILD.jobs()));
        Build older = withFile(new Build(
                "Build", "push", 3, 1, "7654321", "Older", BUILD.createdAt().minusSeconds(120), BUILD.jobs()));
        return buildsRead(List.of(tip, done, older));
    }

    private static Model buildsRead(List<Build> builds) {
        return Update.update(
                        Update.update(model(), new BuildsMsg.Toggled(MAIN)).model(),
                        new BuildsMsg.Read(
                                ORIGIN_MAIN,
                                Option.none(),
                                Option.of(new BuildsPage(builds, builds.size(), Option.none()))))
                .model();
    }

    private static Option<Long> picked(Model model) {
        return model.repoView().branches().builds().get().picked();
    }

    private static Build withFile(Build build) {
        return new Build(
                build.workflow(),
                build.event(),
                build.runId(),
                build.attempt(),
                build.sha(),
                build.title(),
                build.createdAt(),
                build.jobs(),
                Option.of(".github/workflows/publish.yml"));
    }

    /**
     * How many builds the chart on {@code screen} has a column for: the bars of its lowest row.
     */
    private static int bars(String screen) {
        return List.of(screen.split("\n"))
                .filter(line -> line.contains("│█"))
                .last()
                .chars()
                .filter(c -> c == '█')
                .map(c -> 1)
                .sum();
    }
}
