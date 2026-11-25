package net.vanfleteren.jazygit.features.midway.resolve;

import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.text;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.core.git.model.Conflict;
import net.vanfleteren.jazygit.core.git.model.FileEntry;
import net.vanfleteren.jazygit.core.git.model.MarkedFile;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.changes.stage.MarkersLeftPopup;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.popup.Popup;
import net.vanfleteren.jazygit.kernel.ui.RenderedText;
import net.vanfleteren.jazygit.kernel.ui.widgets.ChoiceDialog;
import org.junit.jupiter.api.Test;

/**
 * The dialog of how to resolve a file, and the question staging asks of a file with conflict markers left.
 */
class ResolveDialogsTest {

    private static final Sides REBASE = new Sides(new SideName.Branch(new BranchName("main")), new SideName.Rebased());

    @Test
    void theSidesOfARebaseAreNamedByWhatTheyAre() throws Exception {
        FileEntry file = FileEntry.conflicted("a.txt", new Conflict(Conflict.Side.UPDATED, Conflict.Side.UPDATED));

        String screen = render(ResolveDialogs.RESOLVE, new ResolvePopup(file, REBASE));

        assertTrue(screen.contains("Resolve a.txt"), screen);
        assertTrue(screen.contains("Keep main's version"), screen);
        assertTrue(screen.contains("Keep the rebased commit's version"), screen);
        assertTrue(screen.contains("Edit it"), screen);
    }

    @Test
    void theSideThatDeletedTheFileDeletesIt() throws Exception {
        FileEntry file = FileEntry.conflicted("gone.txt", new Conflict(Conflict.Side.DELETED, Conflict.Side.UPDATED));
        Sides merge =
                new Sides(new SideName.Branch(new BranchName("feature")), new SideName.Commit(new Sha("abcdef1234")));

        String screen = render(ResolveDialogs.RESOLVE, new ResolvePopup(file, merge));

        assertTrue(screen.contains("Delete it, as feature did"), screen);
        assertTrue(screen.contains("Keep abcdef1's version"), screen);
    }

    @Test
    void stagingAFileWithMarkersLeftSaysWhereTheyAre() throws Exception {
        FileEntry file = FileEntry.conflicted("a.txt", new Conflict(Conflict.Side.UPDATED, Conflict.Side.UPDATED));

        String screen = render(
                ResolveDialogs.MARKERS_LEFT,
                new MarkersLeftPopup(List.of(file), Option.none(), List.of(new MarkedFile("a.txt", List.of(7, 15)))));

        assertTrue(screen.contains("a.txt still has conflict markers"), screen);
        assertTrue(screen.contains("line 7, 15"), screen);
        assertTrue(screen.contains("Stage anyway"), screen);
    }

    private static <P extends Popup> String render(ChoiceDialog.Spec<P> spec, P popup) throws Exception {
        ChoiceDialog<P> dialog = new ChoiceDialog<>(spec, msg -> {});
        Model open = TestModels.loaded().openPopup(popup);
        Supplier<Element> view =
                () -> stack(text("background"), dialog.render(open).get());
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(view)) {
            return RenderedText.ofLarge(runner, view);
        }
    }
}
