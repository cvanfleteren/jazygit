package net.vanfleteren.jazygit.feature.branch.checkout;

import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import java.util.ArrayList;
import java.util.Arrays;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.SettledPilot;
import net.vanfleteren.jazygit.ui.widgets.ConfirmDialog;
import org.junit.jupiter.api.Test;

/**
 * The popup asking to switch to the worktree that has the branch checked out: the path of that worktree on a line
 * of its own, unbroken, and the question below it after a blank line.
 */
class SwitchWorktreeDialogTest {

    private static final String WORKTREE =
            "/home/someone/repos/project/.claude/worktrees/theme-esc-back-to-settings-and-more";

    private final java.util.List<Msg> dispatched = new ArrayList<>();
    private final ConfirmDialog<SwitchWorktreePopup> dialog =
            new ConfirmDialog<>(CheckoutDialogs.SWITCH_WORKTREE, dispatched::add);
    private final Model open = TestModels.loaded()
            .openPopup(new SwitchWorktreePopup(new BranchName("worktree-theme-esc-back-to-settings"), WORKTREE));

    @Test
    void showsThePathAndTheQuestionOnLinesOfTheirOwn() throws Exception {
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(this::view)) {
            SettledPilot.focus(runner, CheckoutDialogs.SWITCH_WORKTREE.id());
            java.util.List<String> lines = Arrays.stream(
                            RenderedText.ofLarge(runner, this::view).split("\n"))
                    .map(line -> line.replace("│", "").strip())
                    .toList();

            int path = lines.indexOf(WORKTREE);
            assertTrue(path > 0, String.join("\n", lines));
            assertEquals("This branch is checked out in the worktree at", lines.get(path - 1));
            assertEquals("", lines.get(path + 1));
            assertEquals("Switch to that worktree?", lines.get(path + 2));
        }
    }

    @Test
    void aScreenNarrowerThanThePathStillShowsTheQuestionAndTheHint() throws Exception {
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(this::view)) {
            SettledPilot.focus(runner, CheckoutDialogs.SWITCH_WORKTREE.id());
            String screen = RenderedText.ofSize(runner, this::view, 70, 20);

            assertTrue(screen.contains("Switch to that worktree?"), screen);
            assertTrue(screen.contains("(enter) confirm   (esc) cancel"), screen);
        }
    }

    @Test
    void showsAWorktreeInTheHomeDirectoryFromATilde() {
        Model home = TestModels.loaded()
                .openPopup(new SwitchWorktreePopup(
                        new BranchName("feature"), System.getProperty("user.home") + "/repos/app-feature"));

        assertEquals(
                "This branch is checked out in the worktree at\n~/repos/app-feature\n\nSwitch to that worktree?",
                CheckoutDialogs.SWITCH_WORKTREE
                        .message()
                        .apply(home.popup(SwitchWorktreePopup.class).get()));
    }

    private Element view() {
        return stack(text("background"), dialog.render(open).get());
    }
}
