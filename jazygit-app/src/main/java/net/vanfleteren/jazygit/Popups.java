package net.vanfleteren.jazygit;

import dev.tamboui.toolkit.element.Element;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.feature.Features;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.PopupSlot;

/**
 * Every popup of the app, those of {@link Features#ALL}, with where the focus goes when it closes. At most one is
 * open, so their order does not matter.
 */
final class Popups {

    private final List<PopupSlot> slots;

    Popups(Consumer<Msg> dispatch) {
        this.slots = Features.ALL.flatMap(feature -> feature.popups(dispatch));
    }

    /**
     * Whether the element {@code id} is part of a popup; never for none.
     */
    boolean owns(String id) {
        return slots.exists(slot -> slot.view().ids().contains(id));
    }

    /**
     * The popup {@code model} has open, and where the focus goes now that the frame is drawn with {@code focus}: the
     * open popup takes it, and gives it back to where it was opened from once it closes.
     */
    Rendered render(Model model, Option<String> focus) {
        List<Shown> shown = slots.map(slot -> new Shown(
                slot.view().render(model), slot.view().ids(), slot.returnTo().apply(model), slot.dimsBehind()));
        Option<Shown> open = shown.find(p -> p.element().isDefined());
        Option<String> moveTo = open.fold(
                // A popup that just closed gives the focus back.
                () -> shown.find(p -> focus.exists(p.ids()::contains)).map(Shown::returnTo),
                // A popup that just opened takes the focus.
                p -> focus.exists(p.ids()::contains)
                        ? Option.<String>none()
                        : p.ids().headOption());
        return new Rendered(open.flatMap(Shown::element), open.exists(Shown::dimsBehind), moveTo);
    }

    /**
     * The popups as rendered.
     *
     * @param dialog the open popup, if any
     * @param dimsBehind whether what is behind the open popup is dimmed; never for none
     * @param moveTo where the focus goes, if it moves
     */
    record Rendered(Option<Element> dialog, boolean dimsBehind, Option<String> moveTo) {}

    /**
     * A popup as rendered: its dialog while it is open.
     */
    private record Shown(Option<Element> element, List<String> ids, String returnTo, boolean dimsBehind) {}
}
