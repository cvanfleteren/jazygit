package net.vanfleteren.jazygit.kernel.ui;

import net.vanfleteren.jazygit.kernel.ui.text.ShownTime;
import net.vanfleteren.jazygit.lib.image.Picture;

/**
 * The slots the kernel itself declares, which the app asks of every feature (ADR 202).
 */
public final class KernelSlots {

    /**
     * Whether a view of the feature shows a spinner, so the spinner is kept turning (ADR 171): a feature gives
     * {@code true} while one does.
     */
    public static final Slot<Boolean> SPINNING = Slot.of("kernel.spinning", Boolean.class);

    /**
     * The times a popup of the feature shows, for which the app is woken when they change, as for a screen's (ADR
     * 124, 168, 171).
     */
    public static final Slot<ShownTime> CLOCKING = Slot.of("kernel.clocking", ShownTime.class);

    /**
     * The pictures the views of the feature may draw, which a terminal that draws images itself is sent once they are
     * on the screen (ADR 191).
     */
    public static final Slot<Picture> PICTURING = Slot.of("kernel.picturing", Picture.class);

    private KernelSlots() {}
}
