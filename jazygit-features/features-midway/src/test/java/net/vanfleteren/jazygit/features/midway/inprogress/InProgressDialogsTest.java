package net.vanfleteren.jazygit.features.midway.inprogress;

import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.text;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import io.vavr.control.Option;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.InProgress;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.ui.RenderedText;
import net.vanfleteren.jazygit.kernel.ui.widgets.ChoiceDialog;
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
    void continuingACherryPickCommitsItAndGoesOnWithTheNext() throws Exception {
        // Moving commits cherry-picks a range of them.
        assertContinuing(new InProgress.CherryPicking(new Sha("cccc")), "Commits the cherry-picked commit and goes on");
    }

    @Test
    void continuingARevertCommitsIt() throws Exception {
        assertContinuing(new InProgress.Reverting(new Sha("cccc")), "Commits the revert");
    }

    @Test
    void continuingWithConflictMarkersLeftInAResolvedFileSaysItCommitsThem() throws Exception {
        InProgress rebasing = new InProgress.Rebasing(Option.of(new BranchName("feature")), new Sha("aaaa"), 1, 2);
        Model open =
                TestModels.loaded().openPopup(new InProgressPopup(rebasing, 0, io.vavr.collection.List.of("b.txt")));
        Supplier<Element> view =
                () -> stack(text("background"), dialog.render(open).get());
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(view)) {
            String screen = RenderedText.ofLarge(runner, view);
            assertTrue(screen.contains("Continue, with conflict markers in 1 file"), screen);
            assertTrue(screen.contains("b.txt still has conflict markers"), screen);
        }
    }

    @Test
    void continuingSaysWhereItStoppedAndWhatItGoesOnWith() throws Exception {
        Sha onto = new Sha("0".repeat(40));
        InProgress rebasing = new InProgress.Rebasing(Option.of(new BranchName("feature")), onto, 2, 3);
        net.vanfleteren.jazygit.core.repo.Plan plan = new net.vanfleteren.jazygit.core.repo.Plan(
                onto,
                net.vanfleteren.jazygit.core.repo.Plan.Step.pick(new Sha("1".repeat(40)), "feat touches f"),
                io.vavr.collection.List.of(
                        net.vanfleteren.jazygit.core.repo.Plan.Step.pick(new Sha("2".repeat(40)), "feat 5")),
                2,
                3);
        Model open = TestModels.loaded()
                .openPopup(new InProgressPopup(rebasing, 0, io.vavr.collection.List.empty(), Option.of(plan)));
        Supplier<Element> view =
                () -> stack(text("background"), dialog.render(open).get());
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(view)) {
            String screen = RenderedText.ofLarge(runner, view).replaceAll("\\s+", " ");
            assertTrue(screen.contains("Stopped at 2 of 3: 1111111 feat touches f."), screen);
            assertTrue(screen.contains("Next: 2222222 feat 5."), screen);
        }
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
