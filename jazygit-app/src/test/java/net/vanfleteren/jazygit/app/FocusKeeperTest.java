package net.vanfleteren.jazygit.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import java.util.function.Predicate;
import net.vanfleteren.jazygit.state.AreaId;
import net.vanfleteren.jazygit.state.Focus;
import net.vanfleteren.jazygit.state.PaneId;
import net.vanfleteren.jazygit.ui.CommandLogPanel;
import net.vanfleteren.jazygit.ui.content.ContentSources;
import net.vanfleteren.jazygit.ui.files.FilesPanel;
import org.junit.jupiter.api.Test;

class FocusKeeperTest {

    private static final Predicate<String> POPUP = "confirm"::equals;

    private static final ContentSources SOURCES = ContentSources.standard();

    @Test
    void anElementOfAPopupIsAside() {
        assertEquals(new Focus.Aside("confirm"), FocusKeeper.focusOf("confirm", POPUP, SOURCES));
    }

    @Test
    void theCommandLogIsAside() {
        assertEquals(CommandLogPanel.FOCUS, FocusKeeper.focusOf(CommandLogPanel.ID, POPUP, SOURCES));
    }

    @Test
    void anAreaOfTheContentPanelIsItsArea() {
        assertEquals(AreaId.COMMIT, FocusKeeper.focusOf(AreaId.COMMIT.value(), POPUP, SOURCES));
    }

    /**
     * Which ids are areas the sources the content panel was given say, not those of the app.
     */
    @Test
    void anAreaNoSourceDeclaresIsAPane() {
        assertEquals(
                new PaneId(AreaId.COMMIT.value()),
                FocusKeeper.focusOf(AreaId.COMMIT.value(), POPUP, new ContentSources(List.empty())));
    }

    @Test
    void anythingElseIsAPane() {
        assertEquals(FilesPanel.PANE, FocusKeeper.focusOf(FilesPanel.ID, POPUP, SOURCES));
    }
}
