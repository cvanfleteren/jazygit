package net.vanfleteren.jazygit.app;

import static net.vanfleteren.jazygit.app.TestApp.SIZE;
import static net.vanfleteren.jazygit.app.TestApp.awaitModel;
import static net.vanfleteren.jazygit.app.TestApp.focusedId;
import static net.vanfleteren.jazygit.app.TestApp.onRenderThread;
import static net.vanfleteren.jazygit.app.TestApp.tickless;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tamboui.layout.Rect;
import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.tui.event.MouseButton;
import dev.tamboui.tui.pilot.Pilot;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.gh.StubGitHub;
import net.vanfleteren.jazygit.core.gh.checks.StubChecks;
import net.vanfleteren.jazygit.core.gh.model.Build;
import net.vanfleteren.jazygit.core.gh.model.BuildsPage;
import net.vanfleteren.jazygit.core.gh.process.GhError;
import net.vanfleteren.jazygit.core.git.branches.StubBranches;
import net.vanfleteren.jazygit.core.git.branches.StubRemotes;
import net.vanfleteren.jazygit.core.git.model.Fixtures;
import net.vanfleteren.jazygit.core.git.model.SampleData;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.core.repo.Upstream;
import net.vanfleteren.jazygit.features.github.builds.BuildsView;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.ui.SettledPilot;
import net.vanfleteren.jazygit.kernel.ui.branches.BranchesPanel;
import org.junit.jupiter.api.Test;

/**
 * The builds of a branch take the focus once B shows them, a click on a tab shows its workflow, and B in them shows
 * the log again, with the focus back on the branches (ADR 131).
 */
class JazygitAppBuildsTest {

    private static final BranchName MAIN = new BranchName("main");
    private static final Instant AT = Instant.parse("2026-11-15T08:00:00Z");

    private static Build build(String workflow, long run) {
        return new Build(
                workflow,
                "pull_request",
                run,
                1,
                new Sha("abcdef0"),
                "A commit",
                AT,
                List.of(new Build.Job("job", "SUCCESS", Option.of(AT), Option.of(AT.plusSeconds(60)))));
    }

    private static Env env() {
        Branch main = Fixtures.branch(
                MAIN,
                true,
                new Sha("a1b2c3d4e5f60718293a4b5c6d7e8f9012345678"),
                Instant.EPOCH,
                true,
                true,
                Option.of(new Upstream(new BranchRef.Remote(new RemoteBranch("origin", MAIN)), 0, 0)));
        return new Env(
                SampleData.git()
                        .withBranches(new StubBranches() {
                            @Override
                            public Either<GitError, List<Branch>> local() {
                                return Either.right(List.of(main));
                            }
                        })
                        // origin is the repository on GitHub the stub's checks are of.
                        .withRemotes(new StubRemotes() {
                            @Override
                            public Either<GitError, Map<RemoteName, String>> urls() {
                                return Either.right(
                                        HashMap.of(new RemoteName("origin"), "https://github.com/owner/repo.git"));
                            }
                        }),
                StubGitHub.of().withChecks(new StubChecks() {
                    @Override
                    public Either<GhError, Option<BuildsPage>> builds(String branch, Option<String> after) {
                        return Either.right(Option.of(
                                new BuildsPage(List.of(build("Build", 1), build("Markdown", 2)), 1, Option.none())));
                    }
                }));
    }

    @Test
    void theBuildsTakeTheFocusATabClickedShowsItsWorkflowAndBGivesTheFocusBack() throws Exception {
        TestApp app = new TestApp(env());
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(app::frame, SIZE, tickless())) {
            app.start(testRunner);
            awaitModel(
                    testRunner,
                    app,
                    model -> model.repoView().preview().branchLog().isDefined());
            SettledPilot.focus(testRunner, BranchesPanel.ID, app.drawnAt::size);
            Pilot pilot = SettledPilot.of(testRunner, app.drawnAt::size);

            pilot.press('B');
            awaitModel(
                    testRunner,
                    app,
                    model -> model.part(BuildsView.PART)
                            .exists(view -> view.builds().toOption().isDefined()));
            SettledPilot.settle(testRunner, app.drawnAt::size);
            assertEquals(BuildsView.AREA.value(), onRenderThread(testRunner, () -> focusedId(testRunner)));

            // "✓ Build │ ✓ Markdown": Markdown from the eleventh column of the first line on.
            Rect area = pilot.findElement(BuildsView.AREA.value());
            pilot.mousePress(MouseButton.LEFT, area.left() + 1 + 12, area.top() + 1);
            awaitModel(
                    testRunner,
                    app,
                    model -> model.part(BuildsView.PART)
                            .flatMap(BuildsView::selected)
                            .contains(new BuildsView.Pipeline("Markdown", "pull_request")));

            pilot.press('B');
            awaitModel(testRunner, app, model -> model.part(BuildsView.PART).isEmpty());
            SettledPilot.settle(testRunner, app.drawnAt::size);
            assertEquals(BranchesPanel.ID, onRenderThread(testRunner, () -> focusedId(testRunner)));
        } finally {
            app.onStop();
        }
    }
}
