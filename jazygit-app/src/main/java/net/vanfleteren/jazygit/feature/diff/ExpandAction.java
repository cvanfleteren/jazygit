package net.vanfleteren.jazygit.feature.diff;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.feature.FeatureAction;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.Anchor;
import net.vanfleteren.jazygit.state.DiffCursorAt;

/**
 * Showing the lines a marker of a rich diff leaves out, from its cursor (ADR 77): in every rich diff, and in the diff
 * of a pull request's files next to the keys of its review.
 */
public final class ExpandAction {

    public static final FeatureAction ACTION =
            FeatureAction.ofKeys(keys -> List.of(Scopes.DIFF, Scopes.PULL_REQUEST_DIFF)
                    .foldLeft(
                            keys,
                            (keymap, scope) -> keymap.add(
                                    scope,
                                    onGap("]", "help.diff.expandDown", ExpandMsg.Toward.DOWN),
                                    onGap("[", "help.diff.expandUp", ExpandMsg.Toward.UP),
                                    onGap(Keymap.ENTER, "help.diff.expandAll", ExpandMsg.Toward.ALL))));

    private ExpandAction() {}

    /**
     * {@code key}, which shows the lines {@code toward} says while the cursor is on a marker, and does nothing
     * elsewhere.
     */
    private static Binding<DiffCursorAt> onGap(String key, String descriptionKey, ExpandMsg.Toward toward) {
        return new Binding<>(
                key,
                descriptionKey,
                at -> at.filter(cursor -> cursor.cursor() instanceof Anchor.Gap)
                        .map(cursor -> new ExpandMsg.Asked(cursor, toward)));
    }
}
