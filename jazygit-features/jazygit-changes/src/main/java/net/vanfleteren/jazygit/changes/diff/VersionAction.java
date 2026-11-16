package net.vanfleteren.jazygit.changes.diff;

import io.vavr.collection.List;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.git.model.FileTree;
import net.vanfleteren.jazygit.git.model.Pathed;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.diff.DiffCursorAt;
import net.vanfleteren.jazygit.ui.FeatureAction;
import net.vanfleteren.jazygit.ui.keys.Keymap;
import net.vanfleteren.jazygit.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.ui.keys.Scopes;

/**
 * Showing the new version or the original of the one file of a rich diff in place of its diff (ADR 102), and showing
 * it rendered (ADR 103): in every rich diff, and in the diff of a pull request's files next to the keys of its review.
 * Ctrl+T, as Ctrl+R opens the recent repositories everywhere, and a key the diff leaves goes on to those of everywhere.
 * The same keys work in the files pane, on the file highlighted there, in every area the content panel shows it in:
 * for a file with staged and unstaged changes, where the unstaged area goes, the staged one goes too.
 */
public final class VersionAction {

    public static final FeatureAction ACTION = FeatureAction.ofKeys(keys -> files(diff(keys)));

    private VersionAction() {}

    /**
     * {@code keys} with those of the rich diff and the diff of a pull request's files, acting where the cursor is.
     */
    private static Keymap diff(Keymap keys) {
        return List.of(Scopes.DIFF, Scopes.PULL_REQUEST_DIFF)
                .foldLeft(
                        keys,
                        (keymap, scope) -> keymap.add(
                                        scope,
                                        new Binding<DiffCursorAt>(
                                                "ctrl+o",
                                                "changes.help.diff.version",
                                                at -> at.map(VersionMsg.Cycled::new)))
                                .add(
                                        scope,
                                        new Binding<DiffCursorAt>(
                                                "ctrl+t",
                                                "changes.help.diff.render",
                                                at -> at.map(VersionMsg.RenderToggled::new))));
    }

    /**
     * {@code keys} with those of the files pane, listing the working tree or the files of a commit or a stash.
     */
    private static Keymap files(Keymap keys) {
        return keys.add(
                        Scopes.FILES,
                        onFile("ctrl+o", "changes.help.files.version", VersionMsg.FilesCycled::new),
                        onFile("ctrl+t", "changes.help.files.render", VersionMsg.FilesRenderToggled::new))
                .add(
                        Scopes.COMMIT_FILES,
                        onFile("ctrl+o", "changes.help.commitFiles.version", VersionMsg.FilesCycled::new),
                        onFile("ctrl+t", "changes.help.commitFiles.render", VersionMsg.FilesRenderToggled::new));
    }

    /**
     * The key {@code key}, sending {@code msg} while a file is highlighted in a tree of files, and nothing on a
     * directory.
     */
    private static <T extends Pathed> Binding<FileTree<T>> onFile(
            String key, String descriptionKey, Supplier<Msg> msg) {
        return new Binding<>(
                key,
                descriptionKey,
                selected -> selected.filter(FileTree.File.class::isInstance).map(file -> msg.get()));
    }
}
