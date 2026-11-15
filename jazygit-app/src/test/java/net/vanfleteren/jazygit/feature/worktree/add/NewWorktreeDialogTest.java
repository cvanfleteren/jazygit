package net.vanfleteren.jazygit.feature.worktree.add;

import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.text;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.GhBranch;
import net.vanfleteren.jazygit.gh.model.PrNumber;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.widgets.TextInputDialog;
import org.junit.jupiter.api.Test;

/**
 * The popup asking where a new worktree goes: without a template, it says which key would set one (ADR 134).
 */
class NewWorktreeDialogTest {

    private static final WorktreeSource BRANCH = new WorktreeSource.Existing(new BranchName("feature"));
    private static final WorktreeSource PULL_REQUEST = new WorktreeSource.PullRequest(new PullRequestSummary(
            "octo/repo",
            new PrNumber(9),
            "Title",
            "bob",
            new GhBranch("patch-1"),
            false,
            false,
            PullRequest.Checks.NONE,
            PullRequest.Review.NONE,
            Instant.EPOCH,
            "https://github.com/octo/repo/pull/9"));

    private static String screen(NewWorktreePopup popup) throws Exception {
        Model open = TestModels.loaded(SampleData.git()).openPopup(popup);
        TextInputDialog<NewWorktreePopup> dialog = new TextInputDialog<>(AddWorktreeDialogs.NEW_WORKTREE, msg -> {});
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(
                () -> stack(text("background"), dialog.render(open).get()))) {
            return RenderedText.ofLarge(
                    runner, () -> stack(text("background"), dialog.render(open).get()));
        }
    }

    private static String flat(String screen) {
        return screen.replaceAll("[│\\s]+", " ");
    }

    @Test
    void withoutATemplateItSaysWhichKeyWouldSetOneInABoxUnderTheDialog() throws Exception {
        String screen = flat(screen(new NewWorktreePopup(BRANCH, "/repos/app-feature")));
        assertTrue(
                screen.contains("Set jazygit.newWorktreeTemplate in your git config to change where this starts out."),
                screen);
        // After the input, the dialog closes with its bottom border; the box opens with a border of its own.
        int input = screen.indexOf("/repos/app-feature");
        int closed = screen.indexOf("└", input);
        int box = screen.indexOf("┌", closed);
        int text = screen.indexOf("Set jazygit");
        assertTrue(0 < input && input < closed && closed < box && box < text, screen);

        String forPullRequest = flat(screen(new NewWorktreePopup(PULL_REQUEST, "/repos/app-patch-1")));
        assertTrue(
                forPullRequest.contains("Set jazygit.pullRequestWorktreeTemplate in your git config"), forPullRequest);
    }

    @Test
    void withATemplateItSaysNothingOfIt() throws Exception {
        String screen = screen(new NewWorktreePopup(BRANCH, "/elsewhere/feature", false, true));

        assertTrue(screen.contains("/elsewhere/feature"), screen);
        assertFalse(screen.contains("jazygit.newWorktreeTemplate"), screen);
    }

    @Test
    void theReadTellsThePopupWhetherATemplateWasSet() {
        Model withTemplate = LocationAsked.located(
                        AddWorktreeUpdate.ask(TestModels.loaded(SampleData.git()), BRANCH), Option.of("/elsewhere"))
                .model();
        Model without = LocationAsked.located(AddWorktreeUpdate.ask(TestModels.loaded(SampleData.git()), BRANCH))
                .model();

        assertTrue(withTemplate.popup(NewWorktreePopup.class).exists(NewWorktreePopup::templated));
        assertTrue(without.popup(NewWorktreePopup.class).exists(popup -> !popup.templated()));
    }
}
