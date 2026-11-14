package net.vanfleteren.jazygit.feature.diff;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.FeatureAction;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.diff.DiffCursorAt;
import net.vanfleteren.jazygit.state.view.ContentSearchMsg;

/**
 * Searching what the content panel shows (ADR 126). {@code /} opens the line to type the query in, in every view.
 * The area takes the keys of that line, {@code n} and {@code N} while a search is kept, and Escape to clear it itself,
 * as only it has the matches: {@code n} and {@code N} are listed here for the help, and in the diff of a pull request
 * they are those of its threads, which say they go to the matches while a search is kept.
 */
public final class ContentSearchAction {

    public static final FeatureAction ACTION =
            FeatureAction.ofKeys(keys -> List.of(Scopes.DIFF, Scopes.PULL_REQUEST_DIFF)
                    .foldLeft(
                            keys,
                            (keymap, scope) -> keymap.add(
                                    scope, Binding.always("/", "help.diff.search", ContentSearchMsg.Opened::new)))
                    .add(Scopes.DIFF, inArea("n", "help.diff.nextMatch"), inArea("N", "help.diff.previousMatch")));

    private ContentSearchAction() {}

    /**
     * A key the area acts on itself, which sends nothing from here.
     */
    private static Binding<DiffCursorAt> inArea(String key, String descriptionKey) {
        return new Binding<>(key, descriptionKey, at -> Option.none());
    }
}
