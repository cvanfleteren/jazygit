package net.vanfleteren.jazygit.feature.theme;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.settings.Settings;
import net.vanfleteren.jazygit.settings.ThemeName;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Choosing the theme, which is kept for the next session.
 */
public final class ThemeUpdate {

    private ThemeUpdate() {
    }

    public static Next update(Model model, ThemeMsg msg) {
        return switch (msg) {
            // The key works everywhere, also while a dialog is open, which it does not take away.
            case ThemeMsg.Requested() -> Next.of(model.openPopupIfFree(new ThemePopup(model.settings().theme())));
            case ThemeMsg.Cancelled() -> Next.of(model.withoutPopup(ThemePopup.class));
            case ThemeMsg.Chosen(ThemeName theme) -> choose(model.withoutPopup(ThemePopup.class), theme);
        };
    }

    private static Next choose(Model model, ThemeName theme) {
        Settings chosen = model.settings().withTheme(theme);
        return chosen.equals(model.settings())
                ? Next.of(model)
                : new Next(model.withSettings(chosen), List.of(new Cmd.SaveSettings(model.settings(), chosen)));
    }
}
