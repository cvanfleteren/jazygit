package net.vanfleteren.jazygit.feature.diff;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.feature.FeatureAction;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.DiffCursorAt;

/**
 * Showing the new version or the original of the one file of a rich diff in place of its diff (ADR 102), and showing
 * it rendered (ADR 103): in every rich diff, and in the diff of a pull request's files next to the keys of its review.
 * Ctrl+T, as Ctrl+R opens the recent repositories everywhere, and a key the diff leaves goes on to those of everywhere.
 */
public final class VersionAction {

    public static final FeatureAction ACTION = FeatureAction.ofKeys(keys -> List.of(
                    Scopes.DIFF, Scopes.PULL_REQUEST_DIFF)
            .foldLeft(
                    keys,
                    (keymap, scope) -> keymap.add(
                                    scope,
                                    new Binding<DiffCursorAt>(
                                            "ctrl+o", "help.diff.version", at -> at.map(VersionMsg.Cycled::new)))
                            .add(
                                    scope,
                                    new Binding<DiffCursorAt>(
                                            "ctrl+t",
                                            "help.diff.render",
                                            at -> at.map(VersionMsg.RenderToggled::new)))));

    private VersionAction() {}
}
