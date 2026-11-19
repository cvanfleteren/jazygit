package net.vanfleteren.jazygit.features.chrome.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.util.ArrayList;
import net.vanfleteren.jazygit.core.gh.StubGitHub;
import net.vanfleteren.jazygit.core.git.StubGit;
import net.vanfleteren.jazygit.core.git.branches.StubConfig;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.kernel.settings.ThemeName;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.preferences.RepositoryTheme;
import org.junit.jupiter.api.Test;

/**
 * Keeping the theme of a repository in its git configuration.
 */
class ThemeCmdTest {

    private final java.util.List<String> done = new ArrayList<>();
    private final Env env = new Env(
            StubGit.of().withConfig(new StubConfig() {
                @Override
                public Either<GitFailure, List<ExecutedCommand>> setLocal(String key, Option<String> value) {
                    done.add(key + "=" + value.getOrElse("<unset>"));
                    return Either.right(List.empty());
                }
            }),
            StubGitHub.of());

    @Test
    void aThemeIsKeptByItsId() {
        assertEquals(
                new ThemeMsg.RepositoryThemeSaved(List.empty()),
                new ThemeCmd(Option.of(ThemeName.DARK_COLORBLIND)).run(env));
        assertEquals(java.util.List.of(RepositoryTheme.KEY + "=dark-colorblind"), done);
    }

    @Test
    void noThemeTakesItAway() {
        new ThemeCmd(Option.none()).run(env);

        assertEquals(java.util.List.of(RepositoryTheme.KEY + "=<unset>"), done);
    }
}
