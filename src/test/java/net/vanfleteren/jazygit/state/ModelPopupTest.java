package net.vanfleteren.jazygit.state;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.DeleteBranchPopup;
import net.vanfleteren.jazygit.feature.commit.AmendPopup;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * At most one popup is open, and closing is specific to the kind of popup.
 */
class ModelPopupTest {

    private final Model closed = TestModels.loaded();

    @Test
    void openingAPopupReplacesTheOneThatWasOpen() {
        Model model = closed.openPopup(new AmendPopup()).openPopup(new DeleteBranchPopup("feature"));

        assertEquals(Option.of(new DeleteBranchPopup("feature")), model.popup());
        assertEquals(Option.none(), model.popup(AmendPopup.class));
    }

    @Test
    void closingOnlyClosesAPopupOfThatKind() {
        Model model = closed.openPopup(new DeleteBranchPopup("feature"));

        assertEquals(model, model.withoutPopup(AmendPopup.class));
        assertEquals(Option.none(), model.withoutPopup(DeleteBranchPopup.class).popup());
    }

    @Test
    void theFocusOutsideThePopupsIsKeptThroughAPopup() {
        assertEquals(Option.none(), closed.focusedPane());

        Model focused = Update.update(closed, new Msg.Focused("branches")).model();

        assertEquals(Option.of("branches"), focused.focusedPane());
        assertEquals(Option.of("branches"), focused.openPopup(new AmendPopup()).withoutPopup(AmendPopup.class).focusedPane());
    }
}
