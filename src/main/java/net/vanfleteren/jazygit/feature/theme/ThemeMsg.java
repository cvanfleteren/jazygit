package net.vanfleteren.jazygit.feature.theme;

import net.vanfleteren.jazygit.settings.ThemeName;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

public sealed interface ThemeMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return ThemeUpdate.update(model, this);
    }

    /**
     * The user asked to choose a theme.
     */
    record Requested() implements ThemeMsg {
    }

    record Cancelled() implements ThemeMsg {
    }

    record Chosen(ThemeName theme) implements ThemeMsg {
    }
}
