package net.vanfleteren.jazygit.feature.theme;

import io.vavr.Tuple;
import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.settings.ThemeName;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.PopupSlot;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog.Choice;

/**
 * Popup asking which theme to use.
 */
public final class ThemeDialog {

    // The themes, by the key that chooses them, in the order they are listed.
    private static final Map<Character, ThemeName> KEYS = LinkedHashMap.ofEntries(
            Tuple.of('d', ThemeName.DARK),
            Tuple.of('l', ThemeName.LIGHT),
            Tuple.of('a', ThemeName.DARCULA),
            Tuple.of('b', ThemeName.DARK_COLORBLIND),
            Tuple.of('m', ThemeName.MONO),
            Tuple.of('e', ThemeName.EMBER),
            Tuple.of('p', ThemeName.POWERLINE),
            Tuple.of('n', ThemeName.LIANTIS_DARK),
            Tuple.of('s', ThemeName.SYMFONIC),
            Tuple.of('g', ThemeName.GITHUB_DARK),
            Tuple.of('h', ThemeName.GITHUB_LIGHT),
            Tuple.of('i', ThemeName.GITHUB_DARK_COLORBLIND),
            Tuple.of('j', ThemeName.GITHUB_LIGHT_COLORBLIND),
            Tuple.of('o', ThemeName.NONE));

    // The key that takes the theme of the repository's own away.
    private static final char CLEAR = 'c';

    /**
     * The themes, opening at the one in use, so the arrow keys preview the others from there. For the repository,
     * one of them takes away the theme of its own, if it has one.
     */
    public static final ChoiceDialog.Spec<ThemePopup> SPEC = new ChoiceDialog.Spec<>(
                    "theme",
                    ThemePopup.class,
                    popup -> Messages.get(
                            switch (popup.scope()) {
                                case EVERYWHERE -> "dialog.theme.title";
                                case REPOSITORY -> "dialog.theme.title.repository";
                            }),
                    KEYS.toList()
                            .map(theme -> choice(theme._1, theme._2))
                            .prepend(new Choice<ThemePopup>(
                                            CLEAR,
                                            popup -> Messages.get("dialog.theme.clear", fallback(popup)),
                                            ThemeMsg.Cleared::new)
                                    .withPreview(ThemeMsg.FallbackPreviewed::new)
                                    .shownWhen((model, popup) -> popup.scope() == ThemeScope.REPOSITORY
                                            && popup.repository().own().isDefined())))
            .cancelledBy(ThemeMsg.Cancelled::new)
            .openingAt(popup -> KEYS.find(theme -> theme._2 == popup.current()).map(theme -> theme._1));

    private ThemeDialog() {}

    /**
     * The theme the repository of {@code popup} falls back to without one of its own, and where it comes from.
     */
    private static String fallback(ThemePopup popup) {
        String theme = Messages.get("dialog.theme." + popup.fallback().id());
        return popup.repository()
                .inherited()
                .map(inherited -> Messages.get("dialog.theme.inherited", theme, origin(inherited.origin())))
                .getOrElse(() -> Messages.get("dialog.theme.saved", theme));
    }

    /**
     * {@code origin}, the file of the git configuration a theme is set in, starting with {@code ~} for the home
     * directory, as the user wrote it in an {@code includeIf}.
     */
    public static String origin(String origin) {
        String home = System.getProperty("user.home");
        return origin.startsWith(home + "/") ? "~" + origin.substring(home.length()) : origin;
    }

    /**
     * The popups of this feature, which send what the user does to {@code dispatch}.
     */
    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        // The theme highlighted is previewed on the screen behind, which is not dimmed so it shows as it will be.
        return List.of(
                PopupSlot.returningToFocus(new ChoiceDialog<>(SPEC, dispatch)).undimmed());
    }

    /**
     * The choice of {@code theme}.
     */
    private static Choice<ThemePopup> choice(char key, ThemeName theme) {
        String labelKey = "dialog.theme." + theme.id();
        return new Choice<ThemePopup>(
                        key,
                        popup -> theme == popup.current()
                                ? Messages.get("dialog.theme.current", Messages.get(labelKey))
                                : Messages.get(labelKey),
                        () -> new ThemeMsg.Chosen(theme))
                .withPreview(() -> new ThemeMsg.Previewed(theme));
    }
}
