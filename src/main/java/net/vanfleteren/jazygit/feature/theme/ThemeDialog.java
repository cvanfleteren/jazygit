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
            List.of(choice('d', Theme.DARK), choice('l', Theme.LIGHT), choice('a', Theme.DARCULA)),
            ThemeMsg.Cancelled::new);

    private ThemeDialog() {
    }

    private static Choice<ThemePopup> choice(char key, String theme) {
        return new Choice<>(key,
                popup -> theme.equals(popup.current())
                        ? Messages.get("dialog.theme.current", Messages.get("dialog.theme." + theme))
                        : Messages.get("dialog.theme." + theme),
                () -> new ThemeMsg.Chosen(theme));
    }
}
