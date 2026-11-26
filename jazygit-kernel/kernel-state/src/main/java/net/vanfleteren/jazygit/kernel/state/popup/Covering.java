package net.vanfleteren.jazygit.kernel.state.popup;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.state.Model;

/**
 * A popup over another, which it goes back to once it closes (ADR 206), as the menu of the editors that open a file
 * goes over the popup that was open when it was asked for. The feature of the popup under it keeps telling that one
 * what it learns, as it would while it shows: it finds it {@link #under()} this one, and puts it back
 * {@link #over(Popup) under} it.
 */
public interface Covering extends Popup {

    /**
     * The popup under this one, if any: a {@link Popup#coverable() coverable} one.
     */
    Option<Popup> under();

    /**
     * This popup, with {@code popup} under it in place of the one that was.
     */
    Covering over(Popup popup);

    /**
     * The popup under this one goes on learning how long checks take, as when it shows.
     */
    @Override
    default Popup checkTimesChanging(Model before) {
        return under().<Popup>map(popup -> over(popup.checkTimesChanging(before)))
                .getOrElse(this);
    }
}
