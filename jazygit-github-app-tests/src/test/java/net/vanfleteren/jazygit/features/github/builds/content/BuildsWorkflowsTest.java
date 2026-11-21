package net.vanfleteren.jazygit.features.github.builds.content;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Instant;
import java.util.ArrayList;
import net.vanfleteren.jazygit.core.gh.StubGitHub;
import net.vanfleteren.jazygit.core.gh.checks.StubChecks;
import net.vanfleteren.jazygit.core.gh.model.Build;
import net.vanfleteren.jazygit.core.gh.model.BuildsPage;
import net.vanfleteren.jazygit.core.gh.process.GhError;
import net.vanfleteren.jazygit.core.git.StubGit;
import net.vanfleteren.jazygit.core.git.files.StubDiffs;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.github.GitHubServices;
import net.vanfleteren.jazygit.features.github.builds.BuildsMsg;
import net.vanfleteren.jazygit.features.github.builds.BuildsView.FileAt;
import net.vanfleteren.jazygit.features.github.builds.LoadBuilds;
import net.vanfleteren.jazygit.features.github.builds.ReadWorkflows;
import net.vanfleteren.jazygit.features.github.builds.Workflow;
import net.vanfleteren.jazygit.kernel.content.ContentPanel;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.ui.RenderedText;
import net.vanfleteren.jazygit.kernel.ui.branches.BranchesPanel;
import net.vanfleteren.jazygit.kernel.ui.content.Shown;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import org.junit.jupiter.api.Test;

/**
 * The builds of a branch read the files of their workflows, to show which job needs which, and draw their chart
 * as wide as their area (ADR 131).
 */
class BuildsWorkflowsTest {

    @Test
    void theChartIsDrawnAnewForTheWidthItGets() throws Exception {
        List<Build> builds = List.range(0, 150)
                .map(index -> new Build(
                        "Build",
                        "push",
                        index,
                        1,
                        new Sha("abcdef" + index),
                        "Commit " + index,
                        Instant.parse("2026-11-14T00:00:00Z").plusSeconds(600L * index),
                        BuildsFixtures.BUILD
                                .jobs()
                                .map(job -> new Build.Job(
                                        job.name(),
                                        job.conclusion(),
                                        job.startedAt().map(at -> at.plusSeconds(600L * index - 82800)),
                                        job.completedAt().map(at -> at.plusSeconds(600L * index - 82800))))))
                .reverse();
        Model shown = Update.update(BuildsFixtures.model(), new BuildsMsg.Toggled(BuildsFixtures.MAIN))
                .model();
        Model read = Update.update(
                        shown,
                        new BuildsMsg.Read(
                                BuildsFixtures.ORIGIN_MAIN,
                                Option.none(),
                                Option.of(new BuildsPage(builds, 25, Option.none()))))
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
        Build withFile = BuildsFixtures.withFile(BuildsFixtures.BUILD);
        Model shown = Update.update(BuildsFixtures.model(), new BuildsMsg.Toggled(BuildsFixtures.MAIN))
                .model();

        Next read = Update.update(
                shown,
                new BuildsMsg.Read(
                        BuildsFixtures.ORIGIN_MAIN,
                        Option.none(),
                        Option.of(new BuildsPage(List.of(withFile), 25, Option.of("c25")))));
        Next again = Update.update(
                read.model(),
                new BuildsMsg.Read(
                        BuildsFixtures.ORIGIN_MAIN,
                        Option.of("c25"),
                        Option.of(new BuildsPage(List.empty(), 25, Option.none()))));

        FileAt publish = new FileAt(new Sha("abcdef0"), ".github/workflows/publish.yml");
        assertEquals(
                List.of(new ReadWorkflows(publish), new LoadBuilds(BuildsFixtures.ORIGIN_MAIN, Option.of("c25"))),
                read.cmds());
        assertTrue(again.cmds().isEmpty());
    }

    @Test
    void theWorkflowReadSaysWhichJobOfTheLastBuildNeedsWhich() {
        Build withFile = BuildsFixtures.withFile(new Build(
                "Build",
                "push",
                1,
                1,
                new Sha("abcdef0"),
                "A commit",
                Instant.parse("2026-11-14T23:00:00Z"),
                List.of(
                        BuildsFixtures.BUILD.jobs().head(),
                        new Build.Job(
                                "package",
                                "SUCCESS",
                                Option.of(Instant.parse("2026-11-14T23:04:01Z")),
                                Option.of(Instant.parse("2026-11-14T23:05:00Z"))))));
        Model shown = Update.update(BuildsFixtures.model(), new BuildsMsg.Toggled(BuildsFixtures.MAIN))
                .model();
        Model read = Update.update(
                        shown,
                        new BuildsMsg.Read(
                                BuildsFixtures.ORIGIN_MAIN,
                                Option.none(),
                                Option.of(new BuildsPage(List.of(withFile), 3, Option.none()))))
                .model();
        FileAt publish = new FileAt(new Sha("abcdef0"), ".github/workflows/publish.yml");

        Model known = Update.update(
                        read,
                        new BuildsMsg.WorkflowsRead(HashMap.of(
                                publish, Workflow.parse("jobs:\n  verify: {}\n  package:\n    needs: verify\n"))))
                .model();

        assertEquals(
                HashMap.of(1L, HashMap.of("verify", HashSet.<String>empty(), "package", HashSet.of("verify"))),
                ((BuildsBody) ((Shown.Area) BuildsFixtures.content(known)).body())
                        .shown()
                        .needs());
    }

    @Test
    void readingTheWorkflowsReadsTheFileAndThoseItCallsAtTheCommit() {
        String sha = "abcdef0123456789abcdef0123456789abcdef01";
        Env env = new Env(StubGit.of().withDiffs(new StubDiffs() {
                    @Override
                    public Either<GitError, String> treeText(Sha at, String path) {
                        return at.value().equals(sha) && path.equals(".github/workflows/publish.yml")
                                ? Either.right("jobs:\n  verify:\n    uses: ./.github/workflows/build.yml\n")
                                : at.value().equals(sha) && path.equals(".github/workflows/build.yml")
                                        ? Either.right("jobs:\n  test: {}\n")
                                        : Either.left(new GitError.Unreadable(path, "not there"));
                    }
                }))
                .with(GitHubServices.GH, StubGitHub.of());

        BuildsMsg.WorkflowsRead read = (BuildsMsg.WorkflowsRead)
                new ReadWorkflows(new FileAt(new Sha(sha), ".github/workflows/publish.yml")).run(env);

        assertEquals(2, read.workflows().size());
        assertEquals(
                List.of("test"),
                read.workflows()
                        .get(new FileAt(new Sha(sha), ".github/workflows/build.yml"))
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
        Env env = new Env(StubGit.of()).with(GitHubServices.GH, StubGitHub.of().withChecks(new StubChecks() {
            @Override
            public Either<GhError, String> workflowFile(Sha at, String path) {
                asked.add(path);
                return at.value().equals(sha) && path.equals(".github/workflows/publish.yml")
                        ? Either.right("jobs:\n  verify:\n    uses: ./.github/workflows/build.yml\n")
                        : at.value().equals(sha) && path.equals(".github/workflows/build.yml")
                                ? Either.right("jobs:\n  test: {}\n")
                                : StubChecks.super.workflowFile(at, path);
            }
        }));

        BuildsMsg.WorkflowsRead read = (BuildsMsg.WorkflowsRead)
                new ReadWorkflows(new FileAt(new Sha(sha), ".github/workflows/publish.yml")).run(env);

        assertEquals(
                List.of("test"),
                read.workflows()
                        .get(new FileAt(new Sha(sha), ".github/workflows/build.yml"))
                        .get()
                        .get()
                        .jobs()
                        .map(Workflow.Job::key));
        assertEquals(List.of(".github/workflows/publish.yml", ".github/workflows/build.yml"), List.ofAll(asked));
    }

    @Test
    void aWorkflowGitHasIsNotAskedOfGitHub() {
        String sha = "abcdef0123456789abcdef0123456789abcdef01";
        Env env = new Env(StubGit.of().withDiffs(new StubDiffs() {
                    @Override
                    public Either<GitError, String> treeText(Sha at, String path) {
                        return Either.right("jobs:\n  test: {}\n");
                    }
                }))
                .with(GitHubServices.GH, StubGitHub.of().withChecks(new StubChecks() {
                    @Override
                    public Either<GhError, String> workflowFile(Sha at, String path) {
                        throw new AssertionError("asked GitHub for " + path);
                    }
                }));

        BuildsMsg.WorkflowsRead read = (BuildsMsg.WorkflowsRead)
                new ReadWorkflows(new FileAt(new Sha(sha), ".github/workflows/build.yml")).run(env);

        assertTrue(read.workflows()
                .get(new FileAt(new Sha(sha), ".github/workflows/build.yml"))
                .get()
                .isDefined());
    }

    @Test
    void aWorkflowThatCannotBeReadIsNone() {
        Env env = new Env(StubGit.of()).with(GitHubServices.GH, StubGitHub.of());
        FileAt file = new FileAt(new Sha("abcdef0123456789abcdef0123456789abcdef01"), ".github/workflows/gone.yml");

        assertEquals(new BuildsMsg.WorkflowsRead(HashMap.of(file, Option.none())), new ReadWorkflows(file).run(env));
    }

    @Test
    void aWorkflowGitHubFailsToGiveIsNoneTillTheBuildsShowAgain() {
        // Read once a view, as the builds and the runs are: not asked again on every update while GitHub cannot be
        // reached (ADR 138).
        Env env = new Env(StubGit.of()).with(GitHubServices.GH, StubGitHub.of().withChecks(new StubChecks() {
            @Override
            public Either<GhError, String> workflowFile(Sha at, String path) {
                return Either.left(new GhError.TimedOut("api"));
            }
        }));
        FileAt publish = new FileAt(new Sha("abcdef0"), ".github/workflows/publish.yml");
        Model shown = Update.update(BuildsFixtures.model(), new BuildsMsg.Toggled(BuildsFixtures.MAIN))
                .model();
        Next read = Update.update(
                shown,
                new BuildsMsg.Read(
                        BuildsFixtures.ORIGIN_MAIN,
                        Option.none(),
                        Option.of(new BuildsPage(
                                List.of(BuildsFixtures.withFile(BuildsFixtures.BUILD)), 25, Option.of("c25")))));

        Msg failed = new ReadWorkflows(publish).run(env);
        Next after = Update.update(read.model(), failed);
        Next again = Update.update(
                after.model(),
                new BuildsMsg.Read(
                        BuildsFixtures.ORIGIN_MAIN,
                        Option.of("c25"),
                        Option.of(new BuildsPage(List.empty(), 25, Option.none()))));

        assertEquals(new BuildsMsg.WorkflowsRead(HashMap.of(publish, Option.none())), failed);
        assertTrue(after.cmds()
                .appendAll(again.cmds())
                .filter(ReadWorkflows.class::isInstance)
                .isEmpty());
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
