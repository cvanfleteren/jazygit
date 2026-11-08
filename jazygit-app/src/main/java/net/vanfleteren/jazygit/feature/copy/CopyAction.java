package net.vanfleteren.jazygit.feature.copy;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.feature.FeatureAction;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.DiffCursorAt;

/**
 * Copying what the cursor of a rich diff is on (ADR 94): in every rich diff, and in the diff of a pull request's files
 * next to the keys of its review. A range to copy is opened with {@code v} in every rich diff too; the review binds it
 * in the diff of a pull request's files.
 */
public final class CopyAction {

    public static final FeatureAction ACTION =
            FeatureAction.ofKeys(keys -> List.of(Scopes.DIFF, Scopes.PULL_REQUEST_DIFF)
                    .foldLeft(keys, (keymap, scope) -> keymap.add(scope, copy()))
                    .add(
                            Scopes.DIFF,
                            new Binding<DiffCursorAt>("v", "help.diff.range", at -> at.flatMap(DiffCursorAt::marked))));

    private CopyAction() {}

    private static Binding<DiffCursorAt> copy() {
        return new Binding<>("y", "help.diff.copy", at -> at.map(CopyMsg.Asked::new));
    }
}
