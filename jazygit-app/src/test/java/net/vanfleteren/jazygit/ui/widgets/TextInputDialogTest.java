package net.vanfleteren.jazygit.ui.widgets;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.control.Option;
import lombok.With;
import net.vanfleteren.jazygit.state.ConfirmablePopup;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.OperationCmd;
import net.vanfleteren.jazygit.state.TextPopup;
import org.junit.jupiter.api.Test;

class TextInputDialogTest {

    @Test
    void aConfirmingDialogConfirmsAndDismissesItsPopup() {
        TextInputDialog.Spec<NamePopup> spec =
                TextInputDialog.Spec.confirming("name", NamePopup.class, popup -> "Name", Option.none());

        assertEquals(new Msg.PopupConfirmed(NamePopup.class), spec.onConfirm().get());
        assertEquals(new Msg.PopupDismissed(NamePopup.class), spec.onCancel().get());
    }

    @Test
    void aDialogIsFiftyWideUnlessItsSpecAsksForAnotherWidth() {
        TextInputDialog.Spec<NamePopup> spec =
                TextInputDialog.Spec.confirming("name", NamePopup.class, popup -> "Name", Option.none());

        assertEquals(50, spec.width());
        assertEquals(80, spec.withWidth(80).width());
    }

    @Test
    void anotherCancelKeepsTheWidth() {
        TextInputDialog.Spec<NamePopup> spec = TextInputDialog.Spec.confirming(
                        "name", NamePopup.class, popup -> "Name", Option.none())
                .withWidth(80)
                .cancelledBy(() -> new Msg.PopupDismissed(NamePopup.class));

        assertEquals(80, spec.width());
    }

    @With
    private record NamePopup(String text) implements TextPopup, ConfirmablePopup {

        @Override
        public Option<OperationCmd> confirm() {
            return Option.none();
        }
    }
}
