package net.vanfleteren.jazygit.feature.theme;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.ui.Theme;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog.Choice;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog;

/**
 * Popup asking which theme to use.
 */
public final class ThemeDialog {

    public static final ChoiceDialog.Spec<ThemePopup> SPEC = new ChoiceDialog.Spec<>("theme", ThemePopup.class,
            popup -> Messages.get("dialog.theme.title"),
            List.of(choice('d', Theme.DARK, "dialog.theme.dark"), choice('l', Theme.LIGHT, "dialog.theme.light"),
                    choice('a', Theme.DARCULA, "dialog.theme.darcula"),
                    choice('b', Theme.DARK_COLORBLIND, "dialog.theme.dark-colorblind")),
            ThemeMsg.Cancelled::new);

    private ThemeDialog() {
    }

    /**
     * The choice of {@code theme}, which the message {@code labelKey} names.
     */
    private static Choice<ThemePopup> choice(char key, String theme, String labelKey) {
        return new Choice<>(key,
                popup -> theme.equals(popup.current())
                        ? Messages.get("dialog.theme.current", Messages.get(labelKey))
                        : Messages.get(labelKey),
                () -> new ThemeMsg.Chosen(theme));
    }
}
