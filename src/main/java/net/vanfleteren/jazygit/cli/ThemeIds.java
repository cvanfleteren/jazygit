package net.vanfleteren.jazygit.cli;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.settings.ThemeName;

import java.util.Iterator;

/**
 * The names {@code --theme} takes, in the order the themes are offered.
 */
final class ThemeIds implements Iterable<String> {

    @Override
    public Iterator<String> iterator() {
        return List.of(ThemeName.values()).map(ThemeName::id).iterator();
    }
}
