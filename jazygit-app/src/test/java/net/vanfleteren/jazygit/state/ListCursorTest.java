package net.vanfleteren.jazygit.state;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.control.Option;
import org.junit.jupiter.api.Test;

/**
 * Where the user is in the list of the open popup: kept while that popup shows what changed in it, forgotten when
 * it closes or another one opens.
 */
class ListCursorTest {

    private static final ListCursor MOVED = new ListCursor(Option.of(2), Option.of("ma"));
    private final Model moved = Update.update(
                    TestModels.loaded().openPopup(new First("a")), new Msg.ListCursorChanged(MOVED))
            .model();

    @Test
    void aPopupStartsAtTheTopWithoutAFilter() {
        assertEquals(
                ListCursor.START, TestModels.loaded().openPopup(new First("a")).listCursor());
    }

    @Test
    void movingKeepsWhereTheUserIs() {
        assertEquals(MOVED, moved.listCursor());
    }

    @Test
    void thePopupShowingWhatChangedInItKeepsIt() {
        assertEquals(MOVED, moved.openPopup(new First("b")).listCursor());
    }

    @Test
    void anotherPopupStartsAtTheTop() {
        assertEquals(ListCursor.START, moved.openPopup(new Second()).listCursor());
    }

    @Test
    void closingThePopupForgetsIt() {
        assertEquals(
                ListCursor.START,
                moved.withoutPopup(First.class).openPopup(new First("a")).listCursor());
    }

    @Test
    void movingWithoutAPopupChangesNothing() {
        Model closed = TestModels.loaded();

        assertEquals(
                closed, Update.update(closed, new Msg.ListCursorChanged(MOVED)).model());
    }

    private record First(String shown) implements Popup {}

    private record Second() implements Popup {}
}
