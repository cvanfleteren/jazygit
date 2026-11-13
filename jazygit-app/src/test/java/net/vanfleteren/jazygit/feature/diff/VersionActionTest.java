package net.vanfleteren.jazygit.feature.diff;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.CommitFile;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.FileTree;
import net.vanfleteren.jazygit.git.model.Fixtures;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.Msg;
import org.junit.jupiter.api.Test;

class VersionActionTest {

    private static final Keymap KEYS = Keymap.standard();
    private static final KeyEvent CTRL_O = KeyEvent.ofChar('o', KeyModifiers.CTRL);
    private static final KeyEvent CTRL_T = KeyEvent.ofChar('t', KeyModifiers.CTRL);

    @Test
    void onAFileOfTheWorkingTreeTheKeysActOnItsAreas() {
        Option<FileTree<FileEntry>> file =
                Option.of(new FileTree.File<>("a.md", Fixtures.fileEntry("a.md", ChangeType.MODIFIED)));

        assertEquals(Option.<Msg>of(new VersionMsg.FilesCycled()), KEYS.handle(Scopes.FILES, CTRL_O, file));
        assertEquals(Option.<Msg>of(new VersionMsg.FilesRenderToggled()), KEYS.handle(Scopes.FILES, CTRL_T, file));
    }

    @Test
    void onAFileOfACommitTheKeysActOnItsArea() {
        Option<FileTree<CommitFile>> file =
                Option.of(new FileTree.File<>("a.md", new CommitFile("a.md", ChangeType.MODIFIED)));

        assertEquals(Option.<Msg>of(new VersionMsg.FilesCycled()), KEYS.handle(Scopes.COMMIT_FILES, CTRL_O, file));
        assertEquals(
                Option.<Msg>of(new VersionMsg.FilesRenderToggled()), KEYS.handle(Scopes.COMMIT_FILES, CTRL_T, file));
    }

    @Test
    void onADirectoryTheKeysDoNothing() {
        Option<FileTree<FileEntry>> directory = Option.of(new FileTree.Dir<>("src", "src", List.empty()));

        assertEquals(Option.none(), KEYS.handle(Scopes.FILES, CTRL_O, directory));
        assertEquals(Option.none(), KEYS.handle(Scopes.FILES, CTRL_T, directory));
    }
}
