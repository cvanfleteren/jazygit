package net.vanfleteren.jazygit.features.midway.resolve;

import net.vanfleteren.jazygit.core.git.model.Conflict;
import net.vanfleteren.jazygit.core.git.model.FileEntry;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;

public sealed interface ResolveMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return ResolveUpdate.update(model, this);
    }

    /**
     * The user asked to keep {@code version}'s side of {@code file}.
     */
    record Kept(FileEntry file, Conflict.Version version) implements ResolveMsg {}

    /**
     * The user asked how {@code file} can be resolved.
     */
    record Requested(FileEntry file) implements ResolveMsg {}

    /**
     * The user chose to keep {@code version}'s side of the file of the open {@link ResolvePopup}.
     */
    record Chosen(Conflict.Version version) implements ResolveMsg {}

    /**
     * The user chose to open the file of the open {@link ResolvePopup} in an editor.
     */
    record EditChosen() implements ResolveMsg {}

    /**
     * The user asked to bring back the conflict of the file at {@code path}.
     */
    record RestoreRequested(String path) implements ResolveMsg {}

    /**
     * The user chose to keep {@code version}'s side of the file staging asked about, as it has conflict markers left
     * in it.
     */
    record MarkedKept(Conflict.Version version) implements ResolveMsg {}

    /**
     * The user chose to open the first file staging asked about, as it has conflict markers left in it, in an editor.
     */
    record MarkedEditChosen() implements ResolveMsg {}
}
