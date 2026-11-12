package net.vanfleteren.jazygit.feature.theme;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.settings.ThemeName;
import net.vanfleteren.jazygit.state.popup.Popup;
import net.vanfleteren.jazygit.state.preferences.RepositoryTheme;

/**
 * Asking which theme to use where {@code scope} says, while {@code current} is in use. The highlighted theme shows
 * in its place until one is chosen.
 *
 * @param overridden the theme asked for at startup when the popup opened, which cancelling brings back
 * @param repository the theme the git configuration gives the repository when the popup opened
 * @param saved      the theme kept in the settings, for every repository the git configuration gives none
 * @param behind     the popup the theme was asked for from, like the settings, which cancelling goes back to
 */
public record ThemePopup(
        ThemeName current,
        Option<ThemeName> overridden,
        ThemeScope scope,
        RepositoryTheme repository,
        ThemeName saved,
        Option<Popup> behind)
        implements Popup {

    /**
     * Asking for a theme from no other popup: cancelling closes it.
     */
    public ThemePopup(
            ThemeName current,
            Option<ThemeName> overridden,
            ThemeScope scope,
            RepositoryTheme repository,
            ThemeName saved) {
        this(current, overridden, scope, repository, saved, Option.none());
    }

    /**
     * The theme the repository falls back to without one of its own: the one the user's git configuration gives
     * it, else the saved one.
     */
    public ThemeName fallback() {
        return repository.inherited().map(RepositoryTheme.Inherited::theme).getOrElse(saved);
    }
}
