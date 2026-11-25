package net.vanfleteren.jazygit.kernel.state.popup;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.control.Option;
import org.junit.jupiter.api.Test;

class CheckListTest {

    @Test
    void doesNotScrollToACheckShown() {
        assertEquals(Option.of(0), CheckList.scrolledTo(ListCursor.START, 5, 40).line());
    }

    @Test
    void scrollsDownJustEnoughToShowOneBelow() {
        assertEquals(
                Option.of(5), CheckList.scrolledTo(ListCursor.START, 20, 40).line());
    }

    @Test
    void scrollsUpToOneAbove() {
        assertEquals(
                Option.of(3),
                CheckList.scrolledTo(ListCursor.START.at(10), 3, 40).line());
    }

    @Test
    void neverScrollsPastTheLastFullPage() {
        assertEquals(
                Option.of(4),
                CheckList.scrolledTo(ListCursor.START.at(30), 19, 20).line());
    }
}
