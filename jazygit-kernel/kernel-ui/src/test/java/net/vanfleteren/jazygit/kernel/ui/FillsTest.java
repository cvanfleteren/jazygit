package net.vanfleteren.jazygit.kernel.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import org.junit.jupiter.api.Test;

/**
 * What the features give for the keys of the panes (ADR 172): the first that gives anything, in their order.
 */
class FillsTest {

    private static final PaneKey<String> KEY = PaneKey.of("test.key", String.class);
    private static final PaneKey<String> OTHER = PaneKey.of("test.other", String.class);
    private static final Model MODEL = TestModels.loaded();

    @Test
    void nothingIsGivenForAKeyNoFeatureFills() {
        assertEquals(Option.none(), Fills.NONE.of(KEY, MODEL));
        assertEquals(
                Option.none(),
                Fills.NONE.with(OTHER, model -> Option.of("other")).of(KEY, MODEL));
    }

    @Test
    void theFirstFeatureThatGivesAnythingIsTheOneShown() {
        Fills first = Fills.NONE.with(KEY, model -> Option.none());
        Fills second = Fills.NONE.with(KEY, model -> Option.of("second"));
        Fills third = Fills.NONE.with(KEY, model -> Option.of("third"));

        assertEquals(Option.of("second"), first.andThen(second).andThen(third).of(KEY, MODEL));
        assertEquals(Option.of("third"), third.andThen(second).of(KEY, MODEL));
    }

    @Test
    void aFeatureFillingAKeyTwiceGivesItsFirstAnswerFirst() {
        Fills twice = Fills.NONE.with(KEY, model -> Option.of("one")).with(KEY, model -> Option.of("two"));

        assertEquals(Option.of("one"), twice.of(KEY, MODEL));
    }

    @Test
    void keysOfTheSameNameAndTypeAreTheSameKey() {
        Fills fills = Fills.NONE.with(KEY, model -> Option.of("filled"));

        assertEquals(Option.of("filled"), fills.of(PaneKey.of("test.key", String.class), MODEL));
        assertEquals(Option.none(), fills.of(PaneKey.of("test.key", CharSequence.class), MODEL));
    }
}
