package net.vanfleteren.jazygit.kernel.state.preferences;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.kernel.settings.Settings;
import net.vanfleteren.jazygit.kernel.state.PaneId;

/**
 * Screens the kernel does not know, as a feature brings one (ADR 168), for the tests of the kernel.
 */
public final class TestScreens {

    /**
     * A screen of three panes: {@code top}, {@code middle} and {@code bottom}.
     */
    public static final Screen OTHER = new Screen(
            "other",
            List.of(new PaneId("top"), new PaneId("middle"), new PaneId("bottom")),
            Settings.leftWidth("ui.other-width"),
            Settings.leftHeights("ui.other-heights", List.of(32, 32)));

    private TestScreens() {}
}
