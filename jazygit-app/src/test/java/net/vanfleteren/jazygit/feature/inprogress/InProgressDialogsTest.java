package net.vanfleteren.jazygit.feature.inprogress;

import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.text;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import io.vavr.control.Option;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.InProgress;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog;
import org.junit.jupiter.api.Test;

/**
 * The popup asking what to do with the operation in progress.
 */
class InProgressDialogsTest {

    private final ChoiceDialog<InProgressPopup> dialog = new ChoiceDialog<>(InProgressDialogs.CHOICE, msg -> {});

    @Test
    void continuingARebaseGoesOnWithItsNextCommit() throws Exception {
        assertContinuing(
                new InProgress.Rebasing(Option.of(new BranchName("feature")), new Sha("aaaa"), 1, 2),
                "Goes on with the next commit of the rebase");
    }

    @Test
    void continuingAMergeCommitsIt() throws Exception {
        assertContinuing(new InProgress.Merging(new Sha("cccc")), "Commits the merge");
    }

    @Test
    void continuingACherryPickCommitsIt() throws Exception {
        assertContinuing(new InProgress.CherryPicking(new Sha("cccc")), "Commits the cherry-picked commit");
    }

    @Test
    void continuingARevertCommitsIt() throws Exception {
        assertContinuing(new InProgress.Reverting(new Sha("cccc")), "Commits the revert");
    }

    private void assertContinuing(InProgress inProgress, String details) throws Exception {
        Model open = TestModels.loaded().openPopup(new InProgressPopup(inProgress, 0));
        Supplier<Element> view =
                () -> stack(text("background"), dialog.render(open).get());
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(view)) {
            String screen = RenderedText.ofLarge(runner, view);
            assertTrue(screen.contains(details), screen);
        }
    }
}
