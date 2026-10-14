package net.vanfleteren.jazygit.feature.theme;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.settings.ThemeName;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog.Choice;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog;

/**
 * Popup asking which theme to use.
 */
public final class ThemeDialog {

    public static final ChoiceDialog.Spec<ThemePopup> SPEC = new ChoiceDialog.Spec<>("theme", ThemePopup.class,
            popup -> Messages.get("dialog.theme.title"),
            List.of(choice('d', ThemeName.DARK), choice('l', ThemeName.LIGHT), choice('a', ThemeName.DARCULA),
                    choice('b', ThemeName.DARK_COLORBLIND)),
            ThemeMsg.Cancelled::new);

    private ThemeDialog() {
    }

    /**
     * The choice of {@code theme}.
     */
    private static Choice<ThemePopup> choice(char key, ThemeName theme) {
        String labelKey = "dialog.theme." + theme.id();
        return new Choice<>(key,
                popup -> theme == popup.current()
                        ? Messages.get("dialog.theme.current", Messages.get(labelKey))
                        : Messages.get(labelKey),
                () -> new ThemeMsg.Chosen(theme));
    }
}
