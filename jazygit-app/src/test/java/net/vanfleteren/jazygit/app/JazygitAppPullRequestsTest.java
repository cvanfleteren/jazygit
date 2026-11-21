package net.vanfleteren.jazygit.app;

import static net.vanfleteren.jazygit.app.TestApp.await;
import static net.vanfleteren.jazygit.app.TestApp.awaitModel;
import static net.vanfleteren.jazygit.app.TestApp.focusedId;
import static net.vanfleteren.jazygit.app.TestApp.onRenderThread;
import static net.vanfleteren.jazygit.app.TestApp.tickless;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyModifiers;
import dev.tamboui.tui.pilot.Pilot;
import io.vavr.collection.List;
import io.vavr.control.Either;
import java.time.Instant;
import net.vanfleteren.jazygit.core.gh.StubGitHub;
import net.vanfleteren.jazygit.core.gh.model.GhBranch;
import net.vanfleteren.jazygit.core.gh.model.MyPullRequests;
import net.vanfleteren.jazygit.core.gh.model.PrNumber;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.core.gh.model.PullRequestList;
import net.vanfleteren.jazygit.core.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.core.gh.process.GhError;
import net.vanfleteren.jazygit.core.gh.pullrequests.StubPullRequests;
import net.vanfleteren.jazygit.core.git.model.SampleData;
import net.vanfleteren.jazygit.features.github.prs.PrOverview;
import net.vanfleteren.jazygit.features.github.show.PullRequestPopup;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.view.Preview;
import net.vanfleteren.jazygit.kernel.ui.SettledPilot;
import org.junit.jupiter.api.Test;

/**
 * The pull requests screen, driven through the app: Enter moves the focus to the highlighted pull request, and the
 * other keys of its panes do what the keymap says.
 */
class JazygitAppPullRequestsTest {

    private static final String REVIEW_REQUESTED = "prReviewRequested";

    @Test
    void enterMovesTheFocusToTheHighlightedPullRequestAndALetterDoesWhatTheKeymapSays() throws Exception {
        TestApp app = new TestApp(new Env(SampleData.git(), StubGitHub.of().withPullRequests(new ReviewRequested())));
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(app::frame, TestApp.SIZE, tickless())) {
            app.start(testRunner);
            Pilot pilot = SettledPilot.of(testRunner, app.drawnAt::size);

            pilot.press('p', KeyModifiers.CTRL);
            // The top pane, This repo, has the focus first; the preview is of the pull request highlighted in the
            // focused pane.
            SettledPilot.focus(testRunner, REVIEW_REQUESTED, app.drawnAt::size);
            awaitModel(
                    testRunner,
                    app,
                    model -> model.part(PrOverview.PART)
                            .preview()
                            .map(Preview::shown)
                            .exists(preview -> preview.value().toOption().isDefined()));

            pilot.press(KeyCode.ENTER);
            await(
                    () -> onRenderThread(testRunner, () -> focusedId(testRunner))
                            .equals("pull-request-preview"),
                    "the focus moved to the pull request");

            SettledPilot.focus(testRunner, REVIEW_REQUESTED, app.drawnAt::size);
            pilot.press('o');
            awaitModel(
                    testRunner,
                    app,
                    model -> model.popup(PullRequestPopup.class).isDefined());
        } finally {
            app.onStop();
        }
    }

    @Test
    void qAndCtrlCDoNotQuitFromTheMarkdownDialog() throws Exception {
        TestApp app = new TestApp(new Env(SampleData.git(), StubGitHub.of().withPullRequests(new ReviewRequested())));
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(app::frame, TestApp.SIZE, tickless())) {
            app.start(testRunner);
            Pilot pilot = SettledPilot.of(testRunner, app.drawnAt::size);
            pilot.press('p', KeyModifiers.CTRL);
            SettledPilot.focus(testRunner, REVIEW_REQUESTED, app.drawnAt::size);
            awaitModel(
                    testRunner,
                    app,
                    model -> model.part(PrOverview.PART)
                            .preview()
                            .map(Preview::shown)
                            .exists(preview -> preview.value().toOption().isDefined()));
            pilot.press('o');
            awaitModel(
                    testRunner,
                    app,
                    model -> model.popup(PullRequestPopup.class).isDefined());
            pilot.pause();

            pilot.press('q');
            pilot.press('Q');
            pilot.press('c', KeyModifiers.CTRL);
            pilot.pause();

            assertEquals(0, app.quits.get());
        } finally {
            app.onStop();
        }
    }

    /**
     * GitHub, where the user's review is requested on one pull request.
     */
    private static final class ReviewRequested implements StubPullRequests {

        @Override
        public Either<GhError, MyPullRequests> mine() {
            return Either.right(new MyPullRequests(
                    new PullRequestList(
                            List.of(new PullRequestSummary(
                                    "owner/repo",
                                    new PrNumber(12),
                                    "Fix the login",
                                    "alice",
                                    new GhBranch("fix/login"),
                                    false,
                                    false,
                                    Checks.PASSING,
                                    Review.APPROVED,
                                    Instant.EPOCH,
                                    "https://github.com/owner/repo/pull/12")),
                            1),
                    PullRequestList.EMPTY));
        }
    }
}
