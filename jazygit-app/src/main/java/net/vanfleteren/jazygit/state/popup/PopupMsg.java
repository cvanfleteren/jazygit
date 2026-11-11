package net.vanfleteren.jazygit.state.popup;

import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

/**
 * The user did something in the open popup: moved in its list, typed in it, closed it or confirmed it.
 */
public sealed interface PopupMsg extends Msg {

    /**
     * The user moved in the list of the open popup, or changed its filter.
     */
    record ListCursorChanged(ListCursor cursor) implements PopupMsg {

        @Override
        public Update.Next apply(Model model) {
            return Update.Next.of(model.popup().isDefined() ? model.withListCursor(cursor) : model);
        }
    }

    /**
     * The user changed the text of the open {@link TextPopup} to {@code text}.
     */
    record TextEdited(String text) implements PopupMsg {

        @Override
        public Update.Next apply(Model model) {
            return Update.Next.of(model.popup(TextPopup.class)
                    .map(popup -> model.openPopup(popup.withText(text)))
                    .getOrElse(model));
        }
    }

    /**
     * The user closed the open popup of type {@code type}, by Escape, without doing what it offers (ADR 38). A popup
     * of another type, that opened in the meantime, stays open.
     */
    record Dismissed(Class<? extends Popup> type) implements PopupMsg {

        @Override
        public Update.Next apply(Model model) {
            return Update.Next.of(model.withoutPopup(type));
        }
    }

    /**
     * The user confirmed the open popup of type {@code type}: it closes, and the operation it asks about starts
     * (ADR 38). When no such popup is open, or it asks about none as it stands, nothing happens.
     */
    record Confirmed(Class<? extends ConfirmablePopup> type) implements PopupMsg {

        @Override
        public Update.Next apply(Model model) {
            return Update.confirm(model, type, ConfirmablePopup::confirm);
        }
    }
}
