package net.vanfleteren.jazygit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import org.junit.jupiter.api.Test;

/**
 * Where the focus goes in an open popup: to its first field when it opens, and round to the other end when Tab or
 * Shift+Tab takes it out of the popup.
 */
class PopupFocusTest {

    private static final List<String> FIELDS = List.of("draft", "title", "body");
    private static final Option<String> TITLE = Option.of("title");

    @Test
    void aPopupThatOpensFocusesItsInitialField() {
        assertEquals(TITLE, JazygitApp.popupFocus(FIELDS, TITLE, Option.of("files"), Option.of("files")));
    }

    @Test
    void theFocusStaysWhereItIsInThePopup() {
        assertEquals(Option.none(), JazygitApp.popupFocus(FIELDS, TITLE, Option.of("body"), TITLE));
    }

    @Test
    void tabOutOfTheLastFieldGoesToTheFirst() {
        assertEquals(Option.of("draft"), JazygitApp.popupFocus(FIELDS, TITLE, Option.of("files"), Option.of("body")));
    }

    @Test
    void shiftTabOutOfTheFirstFieldGoesToTheLast() {
        assertEquals(Option.of("body"), JazygitApp.popupFocus(FIELDS, TITLE, Option.of("stash"), Option.of("draft")));
    }

    @Test
    void withTwoFieldsEitherWayGoesToTheOther() {
        List<String> fields = List.of("summary", "description");
        Option<String> summary = Option.of("summary");

        assertEquals(
                Option.of("summary"),
                JazygitApp.popupFocus(fields, summary, Option.of("files"), Option.of("description")));
        assertEquals(Option.of("description"), JazygitApp.popupFocus(fields, summary, Option.of("files"), summary));
    }
}
