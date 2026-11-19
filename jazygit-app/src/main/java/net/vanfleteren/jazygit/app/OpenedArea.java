package net.vanfleteren.jazygit.app;

import io.vavr.control.Option;
import java.util.function.BooleanSupplier;
import net.vanfleteren.jazygit.kernel.state.AreaId;
import net.vanfleteren.jazygit.kernel.state.Focus;
import net.vanfleteren.jazygit.kernel.state.PaneId;

/**
 * What an area that takes the focus for what is opened in it had open at the last frame, and whether it is still to
 * take the focus once it shows it (ADR 155).
 *
 * @param what    what was opened, as {@code ContentArea.opened} said; none while nothing was
 * @param toFocus whether the area is to take the focus from its pane once a frame draws it
 */
record OpenedArea(Option<?> what, boolean toFocus) {

    /**
     * An area with nothing opened in it.
     */
    static final OpenedArea NOTHING = new OpenedArea(Option.none(), false);

    /**
     * Where the focus goes for the area {@code area}, which shows what the pane {@code pane} has highlighted, now that
     * {@code now} is opened in it, as it was {@code before}, while {@code focus} has the focus; {@code drawn} says
     * whether the area shows yet. Something opened in place of what was, or of nothing, takes the focus from the pane
     * once the area shows, if the pane still has it; something closed while the area has the focus gives it back to
     * the pane.
     */
    static Following follow(
            OpenedArea before, Option<?> now, Option<Focus> focus, BooleanSupplier drawn, AreaId area, PaneId pane) {
        boolean changed = !now.equals(before.what());
        if (changed && now.isEmpty()) {
            return new Following(NOTHING, Option.when(focus.contains(area), pane));
        }
        boolean toFocus = changed || before.toFocus();
        if (!toFocus || !drawn.getAsBoolean()) {
            return new Following(new OpenedArea(now, toFocus), Option.none());
        }
        return new Following(new OpenedArea(now, false), Option.when(focus.contains(pane), area));
    }

    /**
     * This, still to take the focus: TamboUI did not move the focus into the area, as no frame drew its element yet.
     */
    OpenedArea waiting() {
        return new OpenedArea(what, true);
    }

    /**
     * What {@link #follow} gives.
     *
     * @param next what the area has open now, to follow at the next frame
     * @param move where the focus is to move, if anywhere
     */
    record Following(OpenedArea next, Option<Focus> move) {}
}
