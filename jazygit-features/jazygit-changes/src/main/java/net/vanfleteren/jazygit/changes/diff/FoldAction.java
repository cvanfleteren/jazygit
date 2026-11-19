package net.vanfleteren.jazygit.changes.diff;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.state.diff.Anchor;
import net.vanfleteren.jazygit.state.diff.DiffCursorAt;
import net.vanfleteren.jazygit.ui.FeatureAction;
import net.vanfleteren.jazygit.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.ui.keys.Scopes;

/**
 * Closing every node of a version of JSON, YAML or XML rendered, or opening them all, {@code z}, in every rich diff and in
 * the diff of a pull request's files (ADR 122, 123). Enter on one node is the key that shows the lines a marker leaves
 * out ({@link ExpandAction}), as a version rendered has no markers.
 */
public final class FoldAction {

    public static final FeatureAction ACTION =
            FeatureAction.ofKeys(keys -> List.of(Scopes.DIFF, Scopes.PULL_REQUEST_DIFF)
                    .foldLeft(
                            keys,
                            (keymap, scope) -> keymap.add(
                                    scope,
                                    new Binding<DiffCursorAt>(
                                            "z",
                                            "changes.help.diff.foldAll",
                                            at -> at.filter(cursor -> cursor.cursor() instanceof Anchor.Node)
                                                    .map(FoldMsg.AllToggled::new)))));

    private FoldAction() {}
}
