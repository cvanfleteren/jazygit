package net.vanfleteren.jazygit.app.keys;

import net.vanfleteren.jazygit.kernel.ui.Feature;
import net.vanfleteren.jazygit.kernel.ui.screen.Screens;

/**
 * The screens the left of the window shows: the repository's, then those the features bring, in the order of
 * {@link Features#ALL} (ADR 168). It is the {@link Screens#standard()} screens, given to the views as a service, as the
 * keymap is (ADR 136).
 */
public final class StandardScreens implements Screens.Provider {

    private static final Screens SCREENS = Screens.of(Features.ALL.flatMap(Feature::screens));

    @Override
    public Screens screens() {
        return SCREENS;
    }
}
