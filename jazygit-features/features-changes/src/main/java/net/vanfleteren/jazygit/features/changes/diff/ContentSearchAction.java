package net.vanfleteren.jazygit.features.changes.diff;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.kernel.state.diff.DiffCursorAt;
import net.vanfleteren.jazygit.kernel.state.view.ContentSearchMsg;
import net.vanfleteren.jazygit.kernel.ui.FeatureAction;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;

/**
 * Searching what the content panel shows (ADR 126). {@code /} opens the line to type the query in, in every view.
 * The area takes the keys of that line and Escape to clear the search itself. {@code n} and {@code N} go where the
 * area worked out they go while a search is kept, as only it has the matches, so the help lists them as acting then;
 * in the diff of a pull request they are those of its threads, which go to the matches the same way.
 */
public final class ContentSearchAction {

    public static final FeatureAction ACTION = FeatureAction.ofKeys(keys -> List.of(
                    Scopes.DIFF, Scopes.PULL_REQUEST_DIFF)
            .foldLeft(
                    keys,
                    (keymap, scope) -> keymap.add(
                            scope, Binding.always("/", "changes.help.diff.search", ContentSearchMsg.Opened::new)))
            .add(
                    Scopes.DIFF,
                    new Binding<DiffCursorAt>("n", "changes.help.diff.nextMatch", at -> at.flatMap(DiffCursorAt::next)),
                    new Binding<DiffCursorAt>(
                            "N", "changes.help.diff.previousMatch", at -> at.flatMap(DiffCursorAt::previous))));

    private ContentSearchAction() {}
}
