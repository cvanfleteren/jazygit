package net.vanfleteren.jazygit.feature.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.util.ArrayList;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.StubConfig;
import net.vanfleteren.jazygit.git.StubGit;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.settings.ThemeName;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.RepositoryTheme;
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
            StubGitHub.empty());

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
