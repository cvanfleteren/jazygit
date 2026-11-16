package net.vanfleteren.jazygit.changes.diff;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.diff.DiffCursorAt;
import net.vanfleteren.jazygit.ui.FeatureAction;
import net.vanfleteren.jazygit.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.ui.keys.Scopes;

/**
 * Going to a line of the file the content panel shows by its number (ADR 140). The area takes {@code :} and the keys
 * of the line to type the number in itself, as only it has the lines and where they are drawn: the key is listed here
 * for the help.
 */
public final class GoToLineAction {

    public static final FeatureAction ACTION = FeatureAction.ofKeys(keys -> List.of(
                    Scopes.DIFF, Scopes.PULL_REQUEST_DIFF)
            .foldLeft(
                    keys,
                    (keymap, scope) -> keymap.add(
                            scope, new Binding<DiffCursorAt>(":", "changes.help.diff.goToLine", at -> Option.none()))));

    private GoToLineAction() {}
}
