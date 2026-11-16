package net.vanfleteren.jazygit.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/**
 * What a feature keeps in the model under its own slice.
 */
class SlicesTest {

    private static final Slice<Integer> COUNT = Slice.of("test.count", 0);

    @Test
    void aSliceNeverChangedHoldsWhatItHoldsInitially() {
        assertEquals(0, Model.initial("repo").get(COUNT));
    }

    @Test
    void aSliceHoldsWhatItWasChangedTo() {
        assertEquals(2, Model.initial("repo").map(COUNT, n -> n + 2).get(COUNT));
    }

    @Test
    void aSliceChangedBackLeavesTheModelEqualToOneNeverChanged() {
        Model model = Model.initial("repo");
        assertEquals(model, model.map(COUNT, n -> n + 1).map(COUNT, n -> n - 1));
    }

    @Test
    void aChangeThatChangesNothingKeepsTheSameModel() {
        Model model = Model.initial("repo").map(COUNT, n -> 3);
        assertSame(model, model.map(COUNT, n -> n));
    }

    @Test
    void itsLensReadsAndChangesIt() {
        Model model = COUNT.lens().map(Model.initial("repo"), n -> n + 5);
        assertEquals(5, COUNT.lens().get(model).get());
    }
}
