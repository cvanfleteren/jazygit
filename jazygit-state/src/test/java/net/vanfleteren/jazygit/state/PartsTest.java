package net.vanfleteren.jazygit.state;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.control.Option;
import org.junit.jupiter.api.Test;

class PartsTest {

    private static final Part<Option<String>> SHOWN = Part.of("test.shown", Option.none());

    private static final Part<Integer> COUNT = Part.of("test.count", 0);

    @Test
    void aPartNeverSetHasItsInitialValue() {
        Model model = Model.initial("repo");

        assertEquals(Option.none(), model.part(SHOWN));
        assertEquals(0, model.part(COUNT));
    }

    @Test
    void aPartKeepsWhatItIsSetTo() {
        Model model = Model.initial("repo").withPart(SHOWN, Option.of("x")).mapPart(COUNT, count -> count + 2);

        assertEquals(Option.of("x"), model.part(SHOWN));
        assertEquals(2, model.part(COUNT));
    }

    @Test
    void aPartSetBackToItsInitialValueLeavesTheModelEqualToOneWithoutIt() {
        Model initial = Model.initial("repo");

        Model back = initial.withPart(SHOWN, Option.of("x")).withPart(SHOWN, Option.none());

        assertEquals(initial, back);
        assertTrue(back.parts().values().isEmpty());
    }

    @Test
    void aPartIsKeptUnderItsNameSoTwoPartsOfOneNameShareTheirValue() {
        Part<Option<String>> same = Part.of("test.shown", Option.none());

        Model model = Model.initial("repo").withPart(SHOWN, Option.of("x"));

        assertEquals(SHOWN, same);
        assertEquals(Option.of("x"), model.part(same));
    }

    @Test
    void theLensOfAPartReadsAndChangesIt() {
        Model model = COUNT.lens().map(Model.initial("repo"), count -> count + 5);

        assertEquals(Option.of(5), COUNT.lens().get(model));
    }
}
