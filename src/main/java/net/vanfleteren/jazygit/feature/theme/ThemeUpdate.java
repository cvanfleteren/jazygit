package net.vanfleteren.jazygit.feature.theme;

import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Choosing the theme.
 */
public final class ThemeUpdate {

    private ThemeUpdate() {
    }

    public static Next update(Model model, ThemeMsg msg) {
        return switch (msg) {
            case ThemeMsg.Requested() -> Next.of(model.openPopup(new ThemePopup(model.theme())));
            case ThemeMsg.Cancelled() -> Next.of(model.withoutPopup(ThemePopup.class));
            case ThemeMsg.Chosen(String theme) -> Next.of(model.withoutPopup(ThemePopup.class).withTheme(theme));
        };
    }
}
