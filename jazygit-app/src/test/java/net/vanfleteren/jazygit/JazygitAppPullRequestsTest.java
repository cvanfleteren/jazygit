package net.vanfleteren.jazygit;

import static net.vanfleteren.jazygit.TestApp.await;
import static net.vanfleteren.jazygit.TestApp.awaitModel;
import static net.vanfleteren.jazygit.TestApp.focusedId;
import static net.vanfleteren.jazygit.TestApp.onRenderThread;
import static net.vanfleteren.jazygit.TestApp.tickless;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyModifiers;
import dev.tamboui.tui.pilot.Pilot;
import io.vavr.collection.List;
import io.vavr.control.Either;
import java.time.Instant;
import net.vanfleteren.jazygit.feature.github.show.PullRequestPopup;
import net.vanfleteren.jazygit.gh.GhError;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.gh.model.MyPullRequests;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.gh.model.PullRequestList;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Preview;
import net.vanfleteren.jazygit.ui.SettledPilot;
import org.junit.jupiter.api.Test;

/**
 * The pull requests screen, driven through the app: Enter moves the focus to the highlighted pull request, and the
 * other keys of its panes do what the keymap says.
 */
class JazygitAppPullRequestsTest {

    private static final String REVIEW_REQUESTED = "prReviewRequested";

    @Test
    void enterMovesTheFocusToTheHighlightedPullRequestAndALetterDoesWhatTheKeymapSays() throws Exception {
        TestApp app = new TestApp(new Env(new SampleData(), new ReviewRequested()));
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(app::frame, TestApp.SIZE, tickless())) {
            app.start(testRunner);
            Pilot pilot = SettledPilot.of(testRunner, app.drawnAt::size);

            pilot.press('p', KeyModifiers.CTRL);
            awaitModel(
                    testRunner,
                    app,
                    model -> model.prOverview()
                            .preview()
                            .map(Preview::shown)
                            .exists(preview -> preview.value().toOption().isDefined()));
            SettledPilot.focus(testRunner, REVIEW_REQUESTED, app.drawnAt::size);

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

    /**
     * GitHub, where the user's review is requested on one pull request.
     */
    private static final class ReviewRequested implements StubGitHub {

        @Override
        public Either<GhError, MyPullRequests> myPullRequests() {
            return Either.right(new MyPullRequests(
                    new PullRequestList(
                            List.of(new PullRequestSummary(
                                    "owner/repo",
                                    12,
                                    "Fix the login",
                                    "alice",
                                    "fix/login",
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
