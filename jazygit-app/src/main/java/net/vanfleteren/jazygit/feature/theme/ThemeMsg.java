package net.vanfleteren.jazygit.feature.theme;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.settings.ThemeName;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

public sealed interface ThemeMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return ThemeUpdate.update(model, this);
    }

    /**
     * The user asked to choose a theme that holds where {@code scope} says.
     */
    record Requested(ThemeScope scope) implements ThemeMsg {}

    record Cancelled() implements ThemeMsg {}

    /**
     * The user highlighted {@code theme}, which shows until they choose one or cancel.
     */
    record Previewed(ThemeName theme) implements ThemeMsg {}

    /**
     * The user highlighted taking away the theme of the repository's own, so the one it falls back to shows.
     */
    record FallbackPreviewed() implements ThemeMsg {}

    record Chosen(ThemeName theme) implements ThemeMsg {}

    /**
     * The user took away the theme of the repository's own, so it falls back to the one it would have without.
     */
    record Cleared() implements ThemeMsg {}

    /**
     * The theme of the repository is kept in its git configuration, by {@code commands}.
     */
    record RepositoryThemeSaved(List<ExecutedCommand> commands) implements ThemeMsg {}

    record RepositoryThemeNotSaved(Failure failure) implements ThemeMsg {}
}
