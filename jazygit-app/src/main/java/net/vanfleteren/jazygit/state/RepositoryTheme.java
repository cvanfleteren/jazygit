package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import io.vavr.control.Option;
import lombok.With;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.git.model.ConfigValue;
import net.vanfleteren.jazygit.settings.ThemeName;

/**
 * The theme the git configuration gives a repository (ADR 70): its own, kept in its configuration, else one the
 * user's configuration gives it, say for every repository in a directory through an {@code includeIf}.
 *
 * @param own       the theme set in the repository's own configuration
 * @param inherited the theme the user's or the system's configuration sets for it, which its own replaces
 */
@With
public record RepositoryTheme(Option<ThemeName> own, Option<Inherited> inherited) {

    /**
     * The key of the theme in the git configuration.
     */
    public static final String KEY = "jazygit.theme";

    /**
     * No theme from the git configuration: the saved one shows.
     */
    public static final RepositoryTheme NONE = new RepositoryTheme(Option.none(), Option.none());

    /**
     * The theme of the repository of {@code git}. A value that names no theme, or that could not be read, gives
     * none, as if it were not set.
     */
    public static RepositoryTheme read(GitInfoProvider git) {
        return new RepositoryTheme(
                git.config().local(KEY).getOrElse(Option.none()).flatMap(ThemeName::of),
                inherited(git.config().all(KEY).getOrElse(List.empty())));
    }

    /**
     * The theme the last of {@code values} outside the repository's own configuration sets: the one git would
     * read if the repository set none.
     */
    static Option<Inherited> inherited(List<ConfigValue> values) {
        return values.filter(value -> !value.local())
                .lastOption()
                .flatMap(value -> ThemeName.of(value.value()).map(theme -> new Inherited(theme, value.origin())));
    }

    /**
     * The theme the repository is drawn in, if the git configuration gives it one.
     */
    public Option<ThemeName> theme() {
        return own.orElse(inherited.map(Inherited::theme));
    }

    /**
     * A theme the user's or the system's git configuration sets.
     *
     * @param theme  the theme
     * @param origin the file that sets it
     */
    public record Inherited(ThemeName theme, String origin) {}
}
