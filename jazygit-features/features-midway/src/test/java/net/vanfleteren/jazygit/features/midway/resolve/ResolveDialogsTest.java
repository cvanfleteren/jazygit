package net.vanfleteren.jazygit.features.midway.resolve;

import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.text;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.core.git.model.Conflict;
import net.vanfleteren.jazygit.core.git.model.ConflictTools;
import net.vanfleteren.jazygit.core.git.model.FileEntry;
import net.vanfleteren.jazygit.core.git.model.MarkedFile;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.changes.edit.FileEditor;
import net.vanfleteren.jazygit.features.changes.stage.MarkersLeftPopup;
import net.vanfleteren.jazygit.kernel.settings.EditorProgram;
import net.vanfleteren.jazygit.kernel.settings.Editors;
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

    private static final FileEditor GIT = new FileEditor.GitEditor();

    private static final FileEntry BOTH =
            FileEntry.conflicted("a b", new Conflict(Conflict.Side.UPDATED, Conflict.Side.UPDATED));

    private static final Sides REBASE = new Sides(new SideName.Branch(new BranchName("main")), new SideName.Rebased());

    @Test
    void theSidesOfARebaseAreNamedByWhatTheyAre() throws Exception {
        FileEntry file = FileEntry.conflicted("a.txt", new Conflict(Conflict.Side.UPDATED, Conflict.Side.UPDATED));

        String screen = render(ResolveDialogs.RESOLVE, popup(file, GIT));

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

        String screen =
                render(ResolveDialogs.RESOLVE, new ResolvePopup(file, merge, GIT, ConflictTools.NONE, Option.none()));

        assertTrue(screen.contains("Delete it, as feature did"), screen);
        assertTrue(screen.contains("Keep abcdef1's version"), screen);
    }

    @Test
    void theCommandsQuoteAPathAsAShellNeedsIt() throws Exception {
        FileEntry file = FileEntry.conflicted("a b", new Conflict(Conflict.Side.UPDATED, Conflict.Side.UPDATED));

        String screen = render(ResolveDialogs.RESOLVE, popup(file, GIT));

        assertTrue(screen.contains("git checkout --ours -- 'a b' && git add -- 'a b'"), screen);
        assertTrue(screen.contains("$(git var GIT_EDITOR) 'a b'"), screen);
    }

    @Test
    void deletingTheFileSaysTheCommandThatDeletesIt() throws Exception {
        FileEntry file = FileEntry.conflicted("gone.txt", new Conflict(Conflict.Side.DELETED, Conflict.Side.UPDATED));

        String screen = render(ResolveDialogs.RESOLVE, popup(file, GIT));

        assertTrue(screen.contains("git rm --quiet -- gone.txt"), screen);
    }

    @Test
    void editingSaysTheCommandOfTheOneEditorSet() throws Exception {
        FileEntry file = FileEntry.conflicted("a b", new Conflict(Conflict.Side.UPDATED, Conflict.Side.UPDATED));

        String screen = render(
                ResolveDialogs.RESOLVE,
                popup(
                        file,
                        new FileEditor.One(
                                EditorProgram.ofFile("Neovim", List.of("nvim", "+{line}"), true, Option.none()))));

        assertTrue(screen.contains("nvim +1 'a b'"), screen);
        assertFalse(screen.contains("GIT_EDITOR"), screen);
    }

    @Test
    void editingWithSeveralEditorsSetSaysOneIsChosen() throws Exception {
        FileEntry file = FileEntry.conflicted("a.txt", new Conflict(Conflict.Side.UPDATED, Conflict.Side.UPDATED));

        String screen = render(
                ResolveDialogs.RESOLVE,
                popup(
                        file,
                        new FileEditor.Several(new Editors(List.of(
                                EditorProgram.ofFile("Neovim", List.of("nvim"), true, Option.none()),
                                EditorProgram.ofFile("Code", List.of("code"), false, Option.none()))))));

        assertTrue(screen.contains("in the editor you choose of those set"), screen);
    }

    @Test
    void theMergeToolAndMergirafAreOfferedWhenThereAreWithTheirCommands() throws Exception {
        String screen = render(
                ResolveDialogs.RESOLVE,
                new ResolvePopup(BOTH, REBASE, GIT, new ConflictTools(true, false, true), Option.of(4)));

        assertTrue(screen.contains("g  Merge it in git's merge tool"), screen);
        assertTrue(screen.contains("git -c mergetool.keepBackup=false mergetool -- 'a b'"), screen);
        assertTrue(screen.contains("m  Solve it with mergiraf"), screen);
        assertTrue(screen.contains("git checkout --conflict=diff3 -- 'a b' && mergiraf solve"), screen);
    }

    @Test
    void neitherIsOfferedWithoutThem() throws Exception {
        String screen = render(ResolveDialogs.RESOLVE, popup(BOTH, GIT));

        assertFalse(screen.contains("merge tool"), screen);
        assertFalse(screen.contains("mergiraf"), screen);
    }

    @Test
    void neitherIsOfferedOnAFileOneSideDeleted() throws Exception {
        FileEntry file = FileEntry.conflicted("gone.txt", new Conflict(Conflict.Side.DELETED, Conflict.Side.UPDATED));

        String screen = render(
                ResolveDialogs.RESOLVE,
                new ResolvePopup(file, REBASE, GIT, new ConflictTools(true, false, true), Option.none()));

        assertFalse(screen.contains("merge tool"), screen);
        assertFalse(screen.contains("mergiraf"), screen);
    }

    @Test
    void editingOpensAtTheFirstConflictInTheEditorSet() throws Exception {
        String screen = render(
                ResolveDialogs.RESOLVE,
                new ResolvePopup(
                        BOTH,
                        REBASE,
                        new FileEditor.One(
                                EditorProgram.ofFile("Neovim", List.of("nvim", "+{line}"), true, Option.none())),
                        ConflictTools.NONE,
                        Option.of(4)));

        assertTrue(screen.contains("nvim +4 'a b'"), screen);
        assertTrue(screen.contains("at its first conflict"), screen);
    }

    @Test
    void gitsEditorSaysItOpensAtTheStartAndWhereTheFirstConflictIs() throws Exception {
        String screen =
                render(ResolveDialogs.RESOLVE, new ResolvePopup(BOTH, REBASE, GIT, ConflictTools.NONE, Option.of(4)));

        assertTrue(screen.contains("Opens it in git's editor, at its start: its first conflict is at"), screen);
        assertTrue(screen.contains("line 4. Staging"), screen);
    }

    private static ResolvePopup popup(FileEntry file, FileEditor editor) {
        return new ResolvePopup(file, REBASE, editor, ConflictTools.NONE, Option.none());
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
