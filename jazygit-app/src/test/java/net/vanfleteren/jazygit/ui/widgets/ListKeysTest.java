package net.vanfleteren.jazygit.ui.widgets;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.ListCursor;
import org.junit.jupiter.api.Test;

class ListKeysTest {

    private static final FilteredList CLOSED = list(Option.none());
    private static final FilteredList OPEN = list(Option.of("ma"));

    @Test
    void escapeClosesTheOpenFilterAndOtherwiseCancels() {
        assertEquals(Option.of(new ListKey.CloseFilter()), classify(KeyEvent.ofKey(KeyCode.ESCAPE), OPEN, true));
        assertEquals(Option.of(new ListKey.Cancel()), classify(KeyEvent.ofKey(KeyCode.ESCAPE), CLOSED, true));
    }

    @Test
    void slashOpensTheFilterOnlyWhenTheListCanFilter() {
        assertEquals(Option.of(new ListKey.OpenFilter()), classify(KeyEvent.ofChar('/'), CLOSED, true));
        assertEquals(Option.none(), classify(KeyEvent.ofChar('/'), CLOSED, false));
    }

    @Test
    void whileTheFilterIsOpenLettersAndTheSideArrowsEditIt() {
        assertEquals(Option.of(new ListKey.EditFilter("mai")), classify(KeyEvent.ofChar('i'), OPEN, true));
        assertEquals(Option.of(new ListKey.EditFilter("ma/")), classify(KeyEvent.ofChar('/'), OPEN, true));
        assertEquals(Option.of(new ListKey.EditFilter("ma")), classify(KeyEvent.ofKey(KeyCode.LEFT), OPEN, true));
    }

    @Test
    void lettersAreLeftToTheListWhileTheFilterIsClosed() {
        assertEquals(Option.none(), classify(KeyEvent.ofChar('m'), CLOSED, true));
    }

    @Test
    void theUpAndDownArrowsMoveWhetherTheFilterIsOpenOrNot() {
        assertEquals(Option.of(new ListKey.Move(2)), classify(KeyEvent.ofKey(KeyCode.DOWN), CLOSED, true));
        assertEquals(Option.of(new ListKey.Move(2)), classify(KeyEvent.ofKey(KeyCode.DOWN), OPEN, true));
        assertEquals(Option.of(new ListKey.Move(0)), classify(KeyEvent.ofKey(KeyCode.UP), OPEN, true));
    }

    @Test
    void enterConfirmsWhetherTheFilterIsOpenOrNot() {
        assertEquals(Option.of(new ListKey.Confirm()), classify(KeyEvent.ofKey(KeyCode.ENTER), CLOSED, true));
        assertEquals(Option.of(new ListKey.Confirm()), classify(KeyEvent.ofKey(KeyCode.ENTER), OPEN, true));
    }

    private static Option<ListKey> classify(KeyEvent event, FilteredList list, boolean canFilter) {
        return ListKeys.classify(event, list, canFilter, new FilterLine());
    }

    /**
     * Three lines, all of which can be highlighted, with the middle one highlighted.
     */
    private static FilteredList list(Option<String> filter) {
        return new FilteredList() {
            @Override
            public ListCursor cursor() {
                return new ListCursor(Option.of(1), filter);
            }

            @Override
            public int selected() {
                return 1;
            }

            @Override
            public int size() {
                return 3;
            }

            @Override
            public boolean highlightable(int line) {
                return true;
            }
        };
    }
}
