package net.vanfleteren.jazygit.feature.theme;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Function;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.settings.Settings;
import net.vanfleteren.jazygit.settings.ThemeName;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.CommandLog;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.RepositoryTheme;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Choosing the theme, which is kept for the next session; it replaces a theme asked for at startup. The highlighted
 * theme shows while the user chooses. A theme chosen for the repository is kept in its git configuration, and
 * shows there instead of the saved one (ADR 70).
 */
public final class ThemeUpdate {

    private ThemeUpdate() {}

    public static Next update(Model model, ThemeMsg msg) {
        return switch (msg) {
            // The key works everywhere, also while a dialog is open, which it does not take away.
            case ThemeMsg.Requested(ThemeScope scope) ->
                Next.of(model.openPopupIfFree(new ThemePopup(
                        model.preferences().theme(),
                        model.preferences().themeOverride(),
                        scope,
                        model.preferences().repositoryTheme(),
                        model.preferences().settings().theme())));
            // Shown as a theme for this session only, which choosing or cancelling replaces.
            case ThemeMsg.Previewed(ThemeName theme) -> Next.of(preview(model, popup -> theme));
            case ThemeMsg.FallbackPreviewed() -> Next.of(preview(model, ThemePopup::fallback));
            case ThemeMsg.Cancelled() ->
                Next.of(model.popup(ThemePopup.class)
                        .map(popup ->
                                model.mapPreferences(preferences -> preferences.withThemeOverride(popup.overridden())))
                        .getOrElse(model)
                        .withoutPopup(ThemePopup.class));
            case ThemeMsg.Chosen(ThemeName theme) ->
                model.popup(ThemePopup.class)
                        .map(popup -> switch (popup.scope()) {
                            case EVERYWHERE -> choose(model.withoutPopup(ThemePopup.class), theme);
                            case REPOSITORY -> own(model.withoutPopup(ThemePopup.class), Option.of(theme));
                        })
                        .getOrElse(() -> Next.of(model));
            case ThemeMsg.Cleared() ->
                model.popup(ThemePopup.class)
                        .map(popup -> own(model.withoutPopup(ThemePopup.class), Option.none()))
                        .getOrElse(() -> Next.of(model));
            case ThemeMsg.RepositoryThemeSaved(List<ExecutedCommand> commands) ->
                Next.of(CommandLog.append(model, ThemeCmd.SET_THEME, commands));
            // What is shown stays what was chosen: the next session shows the theme the configuration still has.
            case ThemeMsg.RepositoryThemeNotSaved(Failure failure) ->
                Next.of(CommandLog.append(model, ThemeCmd.SET_THEME, failure.commands())
                        .fail(new AppError.OperationFailed(ThemeCmd.SET_THEME, Option.none(), failure)));
        };
    }

    /**
     * {@code model} showing the theme {@code theme} gives the open popup; unchanged when it is closed.
     */
    private static Model preview(Model model, Function<ThemePopup, ThemeName> theme) {
        return model.popup(ThemePopup.class)
                .map(popup -> model.mapPreferences(
                        preferences -> preferences.withThemeOverride(Option.of(theme.apply(popup)))))
                .getOrElse(model);
    }

    private static Next choose(Model model, ThemeName theme) {
        Settings chosen = model.preferences().settings().withTheme(theme);
        Model shown = model.mapPreferences(
                preferences -> preferences.withSettings(chosen).withThemeOverride(Option.none()));
        return chosen.equals(model.preferences().settings())
                ? Next.of(shown)
                : new Next(
                        shown, List.of(new Cmd.SaveSettings(model.preferences().settings(), chosen)));
    }

    /**
     * Gives the repository {@code theme} as its own, or none, keeping it in its git configuration.
     */
    private static Next own(Model model, Option<ThemeName> theme) {
        RepositoryTheme before = model.preferences().repositoryTheme();
        Model shown = model.mapPreferences(preferences ->
                preferences.withRepositoryTheme(before.withOwn(theme)).withThemeOverride(Option.none()));
        return before.own().equals(theme) ? Next.of(shown) : new Next(shown, List.of(new ThemeCmd(theme)));
    }
}
