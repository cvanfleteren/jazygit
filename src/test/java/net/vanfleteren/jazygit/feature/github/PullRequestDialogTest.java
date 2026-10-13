package net.vanfleteren.jazygit.feature.github;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Check;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Mergeable;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Reviewer;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.ReviewerState;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.State;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.ui.BranchesPanel;
import net.vanfleteren.jazygit.ui.RenderedText;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;

import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.text;
import static net.vanfleteren.jazygit.state.TestModels.refused;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PullRequestDialogTest {

    private static final Instant NOW = Instant.parse("2026-10-13T12:00:00Z");
    private static final PullRequest PR = new PullRequest(42, "support_rename", true, Checks.FAILING,
            Review.CHANGES_REQUESTED);
    private static final PullRequestDetails DETAILS = new PullRequestDetails(42, "Add rename support to the files panel",
            "cvf", State.OPEN, true, "support_rename", "main", Instant.parse("2026-10-10T08:00:00Z"), 120, 34, 7,
            Mergeable.MERGEABLE, Review.CHANGES_REQUESTED, 5, List.of("enhancement", "ui"),
            List.of(new Reviewer("alice", ReviewerState.APPROVED), new Reviewer("bob", ReviewerState.CHANGES_REQUESTED)),
            List.of(new Check("lint", Checks.PASSING), new Check("integration", Checks.PENDING),
                    new Check("build", Checks.FAILING)));

    private final java.util.List<Msg> dispatched = new ArrayList<>();
    private final PullRequestDialog dialog = new PullRequestDialog(dispatched::add, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void closedModelShowsNoPopup() {
        assertTrue(dialog.render(TestModels.loaded()).isEmpty());
    }

    @Test
    void showsTheBranchWhileTheDetailsAreRead() throws Exception {
        String screen = screen(PullRequestPopup.loading("support_rename", PR));

        assertTrue(screen.contains("Pull request #42"), screen);
        assertTrue(screen.contains("Branch  support_rename"), screen);
        assertTrue(screen.contains("Reading the pull request from GitHub… "
                + BranchesPanel.spinnerFrame(NOW)), screen);
        assertTrue(screen.contains("(enter) open in browser   (esc) close"), screen);
    }

    @Test
    void showsTheDetailsAsLabelledRows() throws Exception {
        String screen = screen(PullRequestPopup.loading("support_rename", PR).with(Either.right(DETAILS)));

        assertTrue(screen.contains("Add rename support to the files panel"), screen);
        assertTrue(screen.contains("    State  Open · Draft"), screen);
        assertTrue(screen.contains("   Author  @cvf"), screen);
        assertTrue(screen.contains("   Branch  support_rename → main"), screen);
        assertTrue(screen.contains("  Created  2026-10-10 (3d ago)"), screen);
        assertTrue(screen.contains("  Changes  +120 −34 in 7 files"), screen);
        assertTrue(screen.contains("Mergeable  yes"), screen);
        assertTrue(screen.contains("   Review  ✎ changes requested"), screen);
        assertTrue(screen.contains("Reviewers  @alice ✔  @bob ✎"), screen);
        assertTrue(screen.contains(" Comments  5"), screen);
        assertTrue(screen.contains("   Labels  enhancement, ui"), screen);
        assertTrue(screen.contains("   Checks  ✗ 1 failed · ● 1 running · ✓ 1 passed"), screen);
        // The failed check first, then the running one; the passed one is only counted.
        assertTrue(screen.indexOf("✗ build") < screen.indexOf("● integration"), screen);
        assertFalse(screen.contains("✓ lint"), screen);
        assertTrue(screen.contains("(enter) open in browser   (esc) close"), screen);
    }

    @Test
    void manyChecksThatDidNotPassAreCut() throws Exception {
        List<Check> checks = List.range(0, 8).map(i -> new Check("check" + i, Checks.FAILING));
        PullRequestDetails details = new PullRequestDetails(42, "t", "", State.OPEN, false, "a", "b", NOW, 0, 0, 0,
                Mergeable.UNKNOWN, Review.NONE, 0, List.empty(), List.empty(), checks);

        String screen = screen(PullRequestPopup.loading("a", PR).with(Either.right(details)));

        assertTrue(screen.contains("check4"), screen);
        assertFalse(screen.contains("check5"), screen);
        assertTrue(screen.contains("… 3 more"), screen);
        assertFalse(screen.contains("Author"), screen);
        assertFalse(screen.contains("Reviewers"), screen);
        assertFalse(screen.contains("Labels"), screen);
    }

    @Test
    void tellsWhyTheDetailsCouldNotBeRead() throws Exception {
        String screen = screen(PullRequestPopup.loading("support_rename", PR).with(Either.left(refused("not logged in"))));

        assertTrue(screen.contains("Could not read the pull request: not logged in"), screen);
        assertTrue(screen.contains("(enter) open in browser   (esc) close"), screen);
    }

    @Test
    void keepsItsSizeWhenTheDetailsArrive() throws Exception {
        List<Check> checks = List.range(0, 8).map(i -> new Check("check" + i, Checks.FAILING));
        PullRequestDetails fullest = new PullRequestDetails(42, "A title long enough to need more than two lines "
                + "at the width of the dialog, so that it must be cut to keep the dialog from growing", "cvf",
                State.OPEN, false, "a", "b", NOW, 0, 0, 0, Mergeable.UNKNOWN, Review.APPROVED, 0, List.of("ui"),
                DETAILS.reviewers(), checks);
        PullRequestPopup loading = PullRequestPopup.loading("support_rename", PR);

        int hintLine = hintLine(screen(loading));

        assertEquals(hintLine, hintLine(screen(loading.with(Either.right(DETAILS)))));
        assertEquals(hintLine, hintLine(screen(loading.with(Either.right(fullest)))));
        assertEquals(hintLine, hintLine(screen(loading.with(Either.left(refused("offline"))))));
    }

    private static int hintLine(String screen) {
        return List.of(screen.split("\n")).indexWhere(line -> line.contains("(enter) open in browser"));
    }

    @Test
    void enterOpensInTheBrowserAndEscapeCloses() throws Exception {
        Model model = TestModels.loaded().openPopup(PullRequestPopup.loading("support_rename", PR));
        try (ToolkitTestRunner runner = start(model)) {
            runner.pilot().press(KeyCode.ENTER);
            runner.pilot().pause();
            runner.pilot().press(KeyCode.ESCAPE);
            runner.pilot().pause();
        }

        assertEquals(java.util.List.of(new PullRequestMsg.OpenConfirmed(), new PullRequestMsg.InfoClosed()), dispatched);
    }

    private String screen(PullRequestPopup popup) throws Exception {
        Model model = TestModels.loaded().openPopup(popup);
        try (ToolkitTestRunner runner = start(model)) {
            return RenderedText.ofLarge(runner, () -> view(model));
        }
    }

    private ToolkitTestRunner start(Model model) throws Exception {
        ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> view(model));
        runner.runner().focusManager().setFocus(PullRequestDialog.ID);
        RenderedText.of(runner, () -> view(model));
        return runner;
    }

    private Element view(Model model) {
        return stack(text("background"), dialog.render(model).get());
    }
}
