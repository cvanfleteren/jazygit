package net.vanfleteren.jazygit.features.changes.diff;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.kernel.state.diff.DiffCursorAt;
import net.vanfleteren.jazygit.kernel.ui.FeatureAction;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;

/**
 * Going to a line of the file the content panel shows by its number (ADR 140): {@code :} opens the line to type the
 * number in where the area shows lines of a file, which the area works out, as it does where the number goes, as only
 * it has the lines and where they are drawn; elsewhere it sends nothing, and the help lists it as disabled.
 */
public final class GoToLineAction {

    public static final FeatureAction ACTION = FeatureAction.ofKeys(keys -> List.of(Scopes.DIFF, Scopes.REMARKED_DIFF)
            .foldLeft(
                    keys,
                    (keymap, scope) -> keymap.add(
                            scope,
                            new Binding<DiffCursorAt>(
                                    ":", "changes.help.diff.goToLine", at -> at.flatMap(DiffCursorAt::goToLine)))));

    private GoToLineAction() {}
}
