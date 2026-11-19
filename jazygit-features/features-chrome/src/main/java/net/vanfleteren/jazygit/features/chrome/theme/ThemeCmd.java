package net.vanfleteren.jazygit.features.chrome.theme;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.settings.ThemeName;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.operation.Operation;
import net.vanfleteren.jazygit.kernel.state.preferences.RepositoryTheme;

/**
 * Keeps {@code theme} as the repository's own in its git configuration, or takes its own away there for none.
 */
public record ThemeCmd(Option<ThemeName> theme) implements Cmd {

    public static final Operation SET_THEME = Operation.of("chrome.log.title.setTheme", "chrome.error.setTheme.failed");

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
