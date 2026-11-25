package net.vanfleteren.jazygit.features.changes.stage;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.FileEntry;
import net.vanfleteren.jazygit.core.git.model.MarkedFile;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;

public sealed interface StageMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return StageUpdate.update(model, this);
    }

    /**
     * The user asked to stage the given files, or to unstage them if they are all staged already.
     *
     * @param files     the changed files of the selection
     * @param directory the repository-relative path of the selected directory, empty for the repository
     *                  root; empty when a single file is selected. Git is then given the directory
     *                  instead of every file in it, where that does the same.
     */
    record Requested(List<FileEntry> files, Option<String> directory) implements StageMsg {

        public Requested(List<FileEntry> files) {
            this(files, Option.none());
        }
    }

    /**
     * The files in conflict among {@code files} were read for conflict markers, before they are staged: {@code marked}
     * are those that have some left (ADR 198).
     */
    record MarkersRead(List<FileEntry> files, Option<String> directory, List<MarkedFile> marked) implements StageMsg {}

    /**
     * The user chose to stage the files of the open {@link MarkersLeftPopup}, conflict markers and all.
     */
    record StagedAnyway() implements StageMsg {}
}
