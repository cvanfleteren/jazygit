package net.vanfleteren.jazygit.feature.theme;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.settings.ThemeName;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.operation.Operation;
import net.vanfleteren.jazygit.state.preferences.RepositoryTheme;

/**
 * Keeps {@code theme} as the repository's own in its git configuration, or takes its own away there for none.
 */
public record ThemeCmd(Option<ThemeName> theme) implements Cmd {

    public static final Operation SET_THEME = Operation.of("log.title.setTheme", "error.setTheme.failed");

    @Override
    public Msg run(Env env) {
        return env.git()
                .config()
                .setLocal(RepositoryTheme.KEY, theme.map(ThemeName::id))
                .mapLeft(Failure::of)
                .fold(this::failed, ThemeMsg.RepositoryThemeSaved::new);
    }

    @Override
    public Msg failed(Failure failure) {
        return new ThemeMsg.RepositoryThemeNotSaved(failure);
    }
}
