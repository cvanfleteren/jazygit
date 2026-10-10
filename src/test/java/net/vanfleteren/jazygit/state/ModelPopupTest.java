package net.vanfleteren.jazygit.state;

import net.vanfleteren.jazygit.feature.branch.DeleteBranchPopup;
import net.vanfleteren.jazygit.feature.commit.AmendPopup;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * At most one popup is open, and closing is specific to the kind of popup.
 */
class ModelPopupTest {

    private final Model closed = TestModels.loaded();

    @Test
    void openingAPopupReplacesTheOneThatWasOpen() {
        Model model = closed.openPopup(new AmendPopup()).openPopup(new DeleteBranchPopup("feature"));

        assertEquals(Optional.of(new DeleteBranchPopup("feature")), model.popup());
        assertEquals(Optional.empty(), model.popup(AmendPopup.class));
    }

    @Test
    void closingOnlyClosesAPopupOfThatKind() {
        Model model = closed.openPopup(new DeleteBranchPopup("feature"));

        assertEquals(model, model.withoutPopup(AmendPopup.class));
        assertEquals(Optional.empty(), model.withoutPopup(DeleteBranchPopup.class).popup());
    }
}
