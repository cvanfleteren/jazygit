package net.vanfleteren.jazygit.feature.theme;

import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.Map;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.settings.ThemeName;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog.Choice;

/**
 * Popup asking which theme to use.
 */
public final class ThemeDialog {

    // The themes, by the key that chooses them, in the order they are listed.
    private static final Map<Character, ThemeName> KEYS = LinkedHashMap.of('d', ThemeName.DARK, 'l', ThemeName.LIGHT,
            'a', ThemeName.DARCULA, 'b', ThemeName.DARK_COLORBLIND, 'm', ThemeName.MONO);

    /**
     * The themes, opening at the one in use, so the arrow keys preview the others from there.
     */
    public static final ChoiceDialog.Spec<ThemePopup> SPEC = new ChoiceDialog.Spec<>("theme", ThemePopup.class,
            popup -> Messages.get("dialog.theme.title"),
            KEYS.toList().map(theme -> choice(theme._1, theme._2)),
            ThemeMsg.Cancelled::new)
            .openingAt(popup -> KEYS.find(theme -> theme._2 == popup.current()).map(theme -> theme._1));

    private ThemeDialog() {
    }

    /**
     * The choice of {@code theme}.
     */
    private static Choice<ThemePopup> choice(char key, ThemeName theme) {
        String labelKey = "dialog.theme." + theme.id();
        return new Choice<ThemePopup>(key,
                popup -> theme == popup.current()
                        ? Messages.get("dialog.theme.current", Messages.get(labelKey))
                        : Messages.get(labelKey),
                () -> new ThemeMsg.Chosen(theme))
                .withPreview(() -> new ThemeMsg.Previewed(theme));
    }
}
