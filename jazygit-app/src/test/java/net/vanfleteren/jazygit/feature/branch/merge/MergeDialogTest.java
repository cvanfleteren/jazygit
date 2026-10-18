package net.vanfleteren.jazygit.feature.branch.merge;

import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.collection.List;
import java.util.ArrayList;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.MergeMode;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.SettledPilot;
import org.junit.jupiter.api.Test;

/**
 * The popup asking how to merge a branch into the checked out one.
 */
class MergeDialogTest {

    private final java.util.List<Msg> dispatched = new ArrayList<>();
    private final net.vanfleteren.jazygit.ui.widgets.ChoiceDialog<MergePopup> dialog =
            new net.vanfleteren.jazygit.ui.widgets.ChoiceDialog<>(MergeDialogs.MERGE, dispatched::add);
    // In the test models, main is checked out.
    private final Model open = Update.update(TestModels.loaded(), new MergeMsg.Requested(new Branch.Name("feature")))
            .model();

    @Test
    void listsTheFourWaysToMergeAndCancel() throws Exception {
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(this::view)) {
            String screen = RenderedText.ofLarge(runner, this::view);
            assertTrue(screen.contains("Merge 'feature' into 'main'"), screen);
            assertTrue(screen.contains("(m) Regular merge (fast-forward)"), screen);
            assertTrue(screen.contains("(n) Regular merge (with merge commit)"), screen);
            assertTrue(screen.contains("(s) Squash merge and leave uncommitted"), screen);
            assertTrue(screen.contains("(S) Squash merge and commit"), screen);
            assertTrue(screen.contains("(c) Cancel"), screen);
        }
    }

    @Test
    void eachLetterChoosesItsWayToMerge() throws Exception {
        assertEquals(List.of(new MergeMsg.Chosen(MergeMode.FAST_FORWARD)), pressing('m'));
        assertEquals(List.of(new MergeMsg.Chosen(MergeMode.MERGE_COMMIT)), pressing('n'));
        assertEquals(List.of(new MergeMsg.Chosen(MergeMode.SQUASH)), pressing('s'));
        assertEquals(List.of(new MergeMsg.Chosen(MergeMode.SQUASH_AND_COMMIT)), pressing('S'));
    }

    @Test
    void escapeAndCCancel() throws Exception {
        assertEquals(List.of(new MergeMsg.Cancelled()), pressing('c'));
        dispatched.clear();
        try (ToolkitTestRunner runner = start()) {
            SettledPilot.of(runner).press(KeyCode.ESCAPE);
            SettledPilot.of(runner).pause();
        }
        assertEquals(java.util.List.of(new MergeMsg.Cancelled()), dispatched);
    }

    private List<Msg> pressing(char key) throws Exception {
        dispatched.clear();
        try (ToolkitTestRunner runner = start()) {
            SettledPilot.of(runner).press(key);
            SettledPilot.of(runner).pause();
        }
        return List.ofAll(dispatched);
    }

    private ToolkitTestRunner start() throws Exception {
        assertTrue(open.popup(MergePopup.class).isDefined());
        ToolkitTestRunner runner = ToolkitTestRunner.runTest(this::view);
        SettledPilot.focus(runner, MergeDialogs.MERGE.id());
        RenderedText.of(runner, this::view);
        return runner;
    }

    private Element view() {
        return stack(text("background"), dialog.render(open).get());
    }
}
