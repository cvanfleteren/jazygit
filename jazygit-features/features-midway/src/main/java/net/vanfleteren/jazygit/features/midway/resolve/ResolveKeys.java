package net.vanfleteren.jazygit.features.midway.resolve;

import io.vavr.control.Option;
import java.util.function.Function;
import net.vanfleteren.jazygit.core.git.model.Conflict;
import net.vanfleteren.jazygit.core.git.model.FileEntry;
import net.vanfleteren.jazygit.core.git.model.FileTree;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;

/**
 * The keys that resolve the file in conflict highlighted in the files pane, and bring back its conflict (ADR 198).
 */
public final class ResolveKeys {

    private ResolveKeys() {}

    public static Keymap register(Keymap keys) {
        return keys.add(
                Scopes.FILES,
                onConflicted(
                        "o", "midway.help.files.keepOurs", file -> new ResolveMsg.Kept(file, Conflict.Version.OURS)),
                onConflicted(
                        "t",
                        "midway.help.files.keepTheirs",
                        file -> new ResolveMsg.Kept(file, Conflict.Version.THEIRS)),
                onConflicted("r", "midway.help.files.resolve", ResolveMsg.Requested::new),
                new Binding<>(
                        "u",
                        "midway.help.files.restore",
                        selection -> file(selection).map(file -> new ResolveMsg.RestoreRequested(file.path()))));
    }

    /**
     * A key on a single file in conflict; on anything else it does nothing.
     */
    private static Binding<FileTree<FileEntry>> onConflicted(
            String key, String descriptionKey, Function<FileEntry, Msg> msg) {
        return new Binding<>(
                key,
                descriptionKey,
                selection -> file(selection)
                        .filter(file -> file.conflict().isDefined())
                        .map(msg));
    }

    private static Option<FileEntry> file(Option<FileTree<FileEntry>> selection) {
        return selection.flatMap(
                node -> node instanceof FileTree.File<FileEntry> file ? Option.of(file.entry()) : Option.none());
    }
}
