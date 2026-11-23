package net.vanfleteren.jazygit.kernel.ui.tabs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tamboui.style.Modifier;
import dev.tamboui.text.Line;
import io.vavr.collection.List;
import io.vavr.control.Option;
import org.junit.jupiter.api.Test;

/**
 * The title of a pane with tabs names them all, the one shown in bold and underlined, and only the names are clicked.
 */
class TabTitlesTest {

    private static final List<String> NAMES = List.of("Commits", "Reflog", "Tags");

    @Test
    void theTitleNamesEveryTabBetweenSeparators() {
        assertEquals("Commits │ Reflog │ Tags", text(TabTitles.line(NAMES, 0)));
    }

    @Test
    void onlyTheTabShownIsBoldAndUnderlined() {
        Line line = TabTitles.line(NAMES, 1);

        assertEquals(
                List.of(false, false, true, false, false),
                List.ofAll(line.spans()).map(span -> span.style().addModifiers().contains(Modifier.UNDERLINED)));
        assertEquals(
                List.of(true, false, false, false, true),
                List.ofAll(line.spans()).map(span -> span.style().addModifiers().contains(Modifier.DIM)));
    }

    @Test
    void onlyTheNamesInTheTitleAreTabs() {
        assertEquals(Option.of(0), TabTitles.at(NAMES, 0));
        assertEquals(Option.of(0), TabTitles.at(NAMES, 6));
        assertEquals(Option.none(), TabTitles.at(NAMES, 7));
        assertEquals(Option.none(), TabTitles.at(NAMES, 8));
        assertEquals(Option.of(1), TabTitles.at(NAMES, 10));
        assertEquals(Option.of(1), TabTitles.at(NAMES, 15));
        assertEquals(Option.none(), TabTitles.at(NAMES, 16));
        assertEquals(Option.of(2), TabTitles.at(NAMES, 19));
        assertEquals(Option.none(), TabTitles.at(NAMES, 23));
        assertEquals(Option.none(), TabTitles.at(NAMES, -1));
    }

    private static String text(Line line) {
        return List.ofAll(line.spans()).map(span -> span.content()).mkString();
    }
}
