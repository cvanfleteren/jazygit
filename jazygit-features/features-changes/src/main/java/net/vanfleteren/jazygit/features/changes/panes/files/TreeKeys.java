package net.vanfleteren.jazygit.features.changes.panes.files;

import dev.tamboui.tui.bindings.Actions;
import dev.tamboui.tui.event.KeyEvent;
import io.vavr.control.Option;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import net.vanfleteren.jazygit.core.git.model.FileTree;
import net.vanfleteren.jazygit.core.git.model.Pathed;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import net.vanfleteren.jazygit.kernel.ui.widgets.PaneKeys;

/**
 * What the keys typed in a tree of the files pane do, which the lists of the working tree and of a commit share: the
 * filter has them first (ADR 105), then the list, and the keys it leaves open the diff or go to the keymap.
 *
 * @param focused  whether the pane has the focus: the trees' key handlers also see keys typed in other panes
 * @param filtered what a key does to the filter of the tree shown, if anything
 * @param openDiff moves the focus to the diff of what the tree highlights
 * @param dispatch receives the messages for the user's actions, on the render thread
 * @param keymap   the keys the features bind
 */
record TreeKeys(
        BooleanSupplier focused,
        Function<KeyEvent, Option<Runnable>> filtered,
        Runnable openDiff,
        Consumer<Msg> dispatch,
        Keymap keymap) {

    /**
     * Has {@code tree} do what a key typed in it does, while the pane has the focus: what {@code own} does with it,
     * once the filter did nothing with it.
     */
    <T extends Pathed> void bind(FileTreeList<T> tree, Function<KeyEvent, Option<Runnable>> own) {
        tree.onKey(PaneKeys.whileFocused(focused, event -> filtered.apply(event).orElse(() -> own.apply(event))));
    }

    /**
     * What the keymap does with {@code event} in {@code scope}, on what {@code tree} highlights.
     */
    <T extends Pathed> Option<Runnable> keyed(Keymap.Scope<FileTree<T>> scope, KeyEvent event, FileTreeList<T> tree) {
        return PaneKeys.sending(keymap.handle(scope, event, tree.selected()), dispatch);
    }

    /**
     * What {@code event} does in {@code tree} once nothing else took it: Enter and Right open the diff of what it
     * highlights, other keys go to the keymap in {@code scope}.
     */
    <T extends Pathed> Option<Runnable> diffOrKeyed(
            Keymap.Scope<FileTree<T>> scope, KeyEvent event, FileTreeList<T> tree) {
        return opensDiff(event, tree) ? Option.of(openDiff) : keyed(scope, event, tree);
    }

    /**
     * Whether {@code event} moves the focus to the diff of what {@code tree} has highlighted: Enter does, and so does
     * Right, except on a collapsed directory, which it expands as in any tree.
     */
    private static boolean opensDiff(KeyEvent event, FileTreeList<?> tree) {
        return tree.selected().isDefined()
                && (event.isConfirm() || (event.matches(Actions.MOVE_RIGHT) && !tree.collapsedSelected()));
    }
}
