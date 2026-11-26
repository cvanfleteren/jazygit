package net.vanfleteren.jazygit.features.midway.resolve;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.Conflict;
import net.vanfleteren.jazygit.core.git.model.ConflictFile;
import net.vanfleteren.jazygit.core.git.model.ConflictTools;
import net.vanfleteren.jazygit.core.git.model.FileEntry;
import net.vanfleteren.jazygit.core.git.model.MarkedFile;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.RepoPath;
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
     * What the file at {@code path} can be handed to was read, where its first conflict starts, and how it stands
     * against the conflict git wrote of it, for its resolve dialog (ADR 201).
     */
    record ToolsRead(RepoPath path, ConflictTools tools, Option<Integer> firstConflict, ConflictFile written)
            implements ResolveMsg {}

    /**
     * The user chose to hand the file of the open {@link ResolvePopup} to git's merge tool.
     */
    record MergeToolChosen() implements ResolveMsg {}

    /**
     * The user chose to solve the file of the open {@link ResolvePopup} with mergiraf.
     */
    record MergirafChosen() implements ResolveMsg {}

    /**
     * {@code cmd} handed the file at {@code path} to a tool and got it back, running {@code commands}: still in
     * conflict or not, and with the markers left in it, if any (ADR 201).
     */
    record HandedBack(
            ResolveCmd cmd, List<ExecutedCommand> commands, RepoPath path, boolean unmerged, Option<MarkedFile> marked)
            implements ResolveMsg {}

    /**
     * The user asked to bring back the conflict of the file at {@code path}.
     */
    record RestoreRequested(RepoPath path) implements ResolveMsg {}

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
