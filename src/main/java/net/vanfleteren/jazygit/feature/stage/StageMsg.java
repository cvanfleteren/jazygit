package net.vanfleteren.jazygit.feature.stage;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

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

}
