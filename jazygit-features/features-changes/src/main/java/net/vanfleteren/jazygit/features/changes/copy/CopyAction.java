package net.vanfleteren.jazygit.features.changes.copy;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.kernel.state.diff.DiffCursorAt;
import net.vanfleteren.jazygit.kernel.ui.FeatureAction;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;

/**
 * Copying what the cursor of a rich diff is on (ADR 94): in every rich diff, and in the diff of a pull request's files
 * next to the keys of its review. A range to copy is opened with {@code v} in every rich diff too; the review binds it
 * in the diff of a pull request's files. The sha of the highlighted commit of the log, or of the reflog's entry
 * (ADR 186), is copied with the same key.
 */
public final class CopyAction {

    public static final FeatureAction ACTION = FeatureAction.ofKeys(keys -> List.of(Scopes.DIFF, Scopes.REMARKED_DIFF)
            .foldLeft(keys, (keymap, scope) -> keymap.add(scope, copy()))
            .add(
                    Scopes.DIFF,
                    new Binding<DiffCursorAt>("v", "changes.help.diff.range", at -> at.flatMap(DiffCursorAt::marked)))
            .add(
                    Scopes.COMMITS,
                    Binding.onSelected(
                            "y", "changes.help.commits.copySha", commit -> new CopyMsg.ShaAsked(commit.sha())))
            .add(
                    Scopes.REFLOG,
                    Binding.onSelected(
                            "y", "changes.help.commits.copySha", entry -> new CopyMsg.ShaAsked(entry.sha()))));

    private CopyAction() {}

    private static Binding<DiffCursorAt> copy() {
        return new Binding<>("y", "changes.help.diff.copy", at -> at.map(CopyMsg.Asked::new));
    }
}
