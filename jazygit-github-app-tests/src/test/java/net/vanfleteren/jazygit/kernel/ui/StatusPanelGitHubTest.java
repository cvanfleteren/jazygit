package net.vanfleteren.jazygit.kernel.ui;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.RepoStatus;
import net.vanfleteren.jazygit.core.repo.PrNumber;
import net.vanfleteren.jazygit.features.github.prs.PrOverview;
import net.vanfleteren.jazygit.features.github.prs.unseen.UnseenPullRequests;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.load.LoadMsg;
import net.vanfleteren.jazygit.kernel.state.preferences.Screen;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.screen.Screens;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import org.junit.jupiter.api.Test;

/**
 * The tests of StatusPanelTest with GitHub in the app (ADR 171).
 */
class StatusPanelGitHubTest {

    /**
     * The names of the app's screens in the title, while {@code unseen} pull requests of the repository are new.
     */
    private static io.vavr.collection.List<StatusPanel.Name> names(int unseen) {
        Model model = TestModels.loaded()
                .withPart(
                        PrOverview.PART,
                        PrOverview.INITIAL.withUnseen(Option.of(new UnseenPullRequests(
                                new PrNumber(unseen),
                                io.vavr.collection.TreeSet.ofAll(io.vavr.collection.List.rangeClosed(1, unseen)
                                        .map(PrNumber::new))))));
        return StatusPanel.names(model, Screens.standard());
    }

    @Test
    void aClickOnTheNameOfAScreenInTheTitleTellsWhichOne() {
        int repository = Messages.get("screen.repository").length();
        int pullRequests = Messages.get("github.screen.pullRequests").length();

        assertEquals(Option.of(Screen.REPOSITORY), StatusPanel.screenAt(0, names(0)));
        assertEquals(Option.of(Screen.REPOSITORY), StatusPanel.screenAt(repository - 1, names(0)));
        assertEquals(Option.none(), StatusPanel.screenAt(repository + 1, names(0)));
        assertEquals(Option.of(PrOverview.SCREEN), StatusPanel.screenAt(repository + 3, names(0)));
        assertEquals(Option.of(PrOverview.SCREEN), StatusPanel.screenAt(repository + 3 + pullRequests - 1, names(0)));
        assertEquals(Option.none(), StatusPanel.screenAt(repository + 3 + pullRequests, names(0)));
        assertEquals(Option.none(), StatusPanel.screenAt(-1, names(0)));
    }

    @Test
    void theNewPullRequestsAreCountedAfterTheNameOfTheirScreenAndAClickOnTheCountShowsIt() {
        String pullRequests = Messages.get("github.screen.pullRequests");
        int start = Messages.get("screen.repository").length() + 3;

        assertEquals(
                Messages.get("screen.repository") + " │ " + pullRequests + " ●2",
                StatusPanel.title(Screen.REPOSITORY, names(2), Theme.SpanStyles.NONE)
                        .rawContent());
        assertEquals(
                Messages.get("screen.repository") + " │ " + pullRequests,
                StatusPanel.title(Screen.REPOSITORY, names(0), Theme.SpanStyles.NONE)
                        .rawContent());
        assertEquals(Option.of(PrOverview.SCREEN), StatusPanel.screenAt(start + pullRequests.length() + 2, names(2)));
        assertEquals(Option.none(), StatusPanel.screenAt(start + pullRequests.length() + 3, names(2)));
    }

    /**
     * The line the status panel shows for {@code status}, with the sample branches main and feature.
     */
    private static String statusLine(RepoStatus status) throws Exception {
        Model model = Update.update(TestModels.loaded(), new LoadMsg.StatusLoaded(status))
                .model();
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> StatusPanel.render(model))) {
            String screen = RenderedText.ofLarge(testRunner, () -> StatusPanel.render(model));
            SettledPilot.of(testRunner).quit();
            return List.of(screen.split("\n"))
                    .map(line -> line.replaceAll("^[│ ]+|[│ ]+$", ""))
                    .find(line -> line.startsWith("repo →"))
                    .getOrElse(screen);
        }
    }
}
