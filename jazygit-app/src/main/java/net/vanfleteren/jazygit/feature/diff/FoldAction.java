package net.vanfleteren.jazygit.feature.diff;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.feature.FeatureAction;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.diff.Anchor;
import net.vanfleteren.jazygit.state.diff.DiffCursorAt;

/**
 * Closing every node of a version of JSON or YAML rendered, or opening them all, {@code z}, in every rich diff and in
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
                                            "help.diff.foldAll",
                                            at -> at.filter(cursor -> cursor.cursor() instanceof Anchor.Node)
                                                    .map(FoldMsg.AllToggled::new)))));

    private FoldAction() {}
}
