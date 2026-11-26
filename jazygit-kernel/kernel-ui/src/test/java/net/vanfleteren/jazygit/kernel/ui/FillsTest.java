package net.vanfleteren.jazygit.kernel.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import org.junit.jupiter.api.Test;

/**
 * What the features give for the slots (ADR 172, 202): the first that is given, or all that is, in the order of the
 * features.
 */
class FillsTest {

    private static final Slot<String> KEY = Slot.of("test.key");
    private static final Slot<String> OTHER = Slot.of("test.other");
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
    void allThatGiveSomethingAreKeptInTheOrderOfTheFeatures() {
        Fills first = Fills.NONE.with(KEY, model -> Option.none());
        Fills second = Fills.NONE.with(KEY, model -> Option.of("second"));
        Fills third = Fills.NONE.with(KEY, model -> Option.of("third"));

        assertEquals(
                List.of("second", "third"), first.andThen(second).andThen(third).all(KEY, MODEL));
        assertEquals(List.empty(), first.all(KEY, MODEL));
        assertEquals(List.empty(), Fills.NONE.all(KEY, MODEL));
    }

    @Test
    void aFillerMayGiveSeveralWhichAllKeepsInTheirOrder() {
        Fills fills = Fills.NONE
                .with(KEY, model -> List.of("one", "two"))
                .andThen(Fills.NONE.with(KEY, model -> List.empty()))
                .andThen(Fills.NONE.with(KEY, model -> Option.of("three")));

        assertEquals(List.of("one", "two", "three"), fills.all(KEY, MODEL));
        assertEquals(Option.of("one"), fills.of(KEY, MODEL));
    }

    @Test
    void aFeatureFillingAKeyTwiceGivesItsFirstAnswerFirst() {
        Fills twice = Fills.NONE.with(KEY, model -> Option.of("one")).with(KEY, model -> Option.of("two"));

        assertEquals(Option.of("one"), twice.of(KEY, MODEL));
    }

    @Test
    void slotsOfTheSameNameAreTheSameSlot() {
        Fills fills = Fills.NONE.with(KEY, model -> Option.of("filled"));

        assertEquals(Option.of("filled"), fills.of(Slot.<String>of("test.key"), MODEL));
    }
}
