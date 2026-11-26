package net.vanfleteren.jazygit.kernel.state.popup;

import static net.vanfleteren.jazygit.kernel.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.state.Model;
import org.junit.jupiter.api.Test;

/**
 * A popup over another, which goes back to it (ADR 206).
 */
class CoveringTest {

    @Test
    void thePopupUnderOneOverItIsToldWhatChecksTakeAsWhenItShows() {
        Model before = loaded();
        Menu menu = new Menu(Option.of(new Timed(false)));

        assertEquals(new Menu(Option.of(new Timed(true))), menu.checkTimesChanging(before));
    }

    @Test
    void oneOverNothingStaysAsItIs() {
        Menu menu = new Menu(Option.none());

        assertEquals(menu, menu.checkTimesChanging(loaded()));
    }

    /**
     * A popup that says whether it was told what checks take.
     */
    private record Timed(boolean told) implements Popup {
        @Override
        public Popup checkTimesChanging(Model before) {
            return new Timed(true);
        }
    }

    /**
     * A popup over {@code under}.
     */
    private record Menu(Option<Popup> under) implements Covering {
        @Override
        public Covering over(Popup popup) {
            return new Menu(Option.of(popup));
        }
    }
}
