package net.vanfleteren.jazygit.state;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.delete.DeleteBranchPopup;
import net.vanfleteren.jazygit.feature.commit.amend.AmendPopup;
import net.vanfleteren.jazygit.feature.commit.amend.AmendTarget;
import net.vanfleteren.jazygit.git.model.BranchName;
import org.junit.jupiter.api.Test;

/**
 * At most one popup is open, and closing is specific to the kind of popup.
 */
class ModelPopupTest {

    private final Model closed = TestModels.loaded();

    @Test
    void openingAPopupReplacesTheOneThatWasOpen() {
        Model model = closed.openPopup(new AmendPopup(new AmendTarget.LastCommit()))
                .openPopup(new DeleteBranchPopup(new BranchName("feature")));

        assertEquals(Option.of(new DeleteBranchPopup(new BranchName("feature"))), model.popup());
        assertEquals(Option.none(), model.popup(AmendPopup.class));
    }

    @Test
    void closingOnlyClosesAPopupOfThatKind() {
        Model model = closed.openPopup(new DeleteBranchPopup(new BranchName("feature")));

        assertEquals(model, model.withoutPopup(AmendPopup.class));
        assertEquals(Option.none(), model.withoutPopup(DeleteBranchPopup.class).popup());
    }

    @Test
    void theFocusOutsideThePopupsIsKeptThroughAPopup() {
        assertEquals(Option.none(), closed.repoView().focusedPane());

        Model focused =
                Update.update(closed, new Msg.Focused("branches", false)).model();

        assertEquals(Option.of("branches"), focused.repoView().focusedPane());
        assertEquals(
                Option.of("branches"),
                focused.openPopup(new AmendPopup(new AmendTarget.LastCommit()))
                        .withoutPopup(AmendPopup.class)
                        .repoView()
                        .focusedPane());
    }
}
