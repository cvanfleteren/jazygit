package net.vanfleteren.jazygit.state;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.StubConfig;
import net.vanfleteren.jazygit.git.StubGit;
import net.vanfleteren.jazygit.git.model.ConfigValue;
import net.vanfleteren.jazygit.settings.ThemeName;
import org.junit.jupiter.api.Test;

/**
 * The theme the git configuration gives a repository.
 */
class RepositoryThemeTest {

    private static final ConfigValue USER = new ConfigValue(false, "/home/alice/.gitconfig", "dark");
    private static final ConfigValue WORK = new ConfigValue(false, "/home/alice/.gitconfig-work", "light");
    private static final ConfigValue OWN = new ConfigValue(true, ".git/config", "ember");

    private static RepositoryTheme read(Option<String> local, List<ConfigValue> all) {
        return RepositoryTheme.read(StubGit.of().withConfig(new StubConfig() {
            @Override
            public Either<GitError, Option<String>> local(String key) {
                return Either.right(local.filter(value -> key.equals(RepositoryTheme.KEY)));
            }

            @Override
            public Either<GitError, List<ConfigValue>> all(String key) {
                return Either.right(key.equals(RepositoryTheme.KEY) ? all : List.empty());
            }
        }));
    }

    @Test
    void noThemeAnywhereIsNone() {
        assertEquals(RepositoryTheme.NONE, read(Option.none(), List.empty()));
        assertEquals(Option.none(), RepositoryTheme.NONE.theme());
    }

    @Test
    void theLastThemeOutsideTheRepositoryIsInherited() {
        RepositoryTheme theme = read(Option.none(), List.of(USER, WORK));

        assertEquals(
                new RepositoryTheme(
                        Option.none(),
                        Option.of(new RepositoryTheme.Inherited(ThemeName.LIGHT, "/home/alice/.gitconfig-work"))),
                theme);
        assertEquals(Option.of(ThemeName.LIGHT), theme.theme());
    }

    @Test
    void theOwnThemeHoldsOverTheInheritedOne() {
        RepositoryTheme theme = read(Option.of("ember"), List.of(USER, OWN));

        assertEquals(Option.of(ThemeName.EMBER), theme.own());
        assertEquals(Option.of(ThemeName.DARK), theme.inherited().map(RepositoryTheme.Inherited::theme));
        assertEquals(Option.of(ThemeName.EMBER), theme.theme());
    }

    @Test
    void aValueThatNamesNoThemeIsAsIfItWereNotSet() {
        RepositoryTheme theme = read(
                Option.of("purple"), List.of(USER, new ConfigValue(false, "/home/alice/.gitconfig-work", "purple")));

        assertEquals(RepositoryTheme.NONE, theme);
    }
}
