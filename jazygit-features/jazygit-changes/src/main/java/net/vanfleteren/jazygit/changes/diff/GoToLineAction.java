package net.vanfleteren.jazygit.changes.diff;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.state.diff.DiffCursorAt;
import net.vanfleteren.jazygit.state.view.LineNumberMsg;
import net.vanfleteren.jazygit.ui.FeatureAction;
import net.vanfleteren.jazygit.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.ui.keys.Scopes;

/**
 * Going to a line of the file the content panel shows by its number (ADR 140): {@code :} opens the line to type the
 * number in where the area has a cursor, which a view without lines of a file has none of. The area takes the keys of
 * that line itself, and works out where the number goes, as only it has the lines and where they are drawn.
 */
public final class GoToLineAction {

    public static final FeatureAction ACTION =
            FeatureAction.ofKeys(keys -> List.of(Scopes.DIFF, Scopes.PULL_REQUEST_DIFF)
                    .foldLeft(
                            keys,
                            (keymap, scope) -> keymap.add(
                                    scope,
                                    new Binding<DiffCursorAt>(
                                            ":",
                                            "changes.help.diff.goToLine",
                                            at -> at.map(ignored -> new LineNumberMsg.Opened())))));

    private GoToLineAction() {}
}
