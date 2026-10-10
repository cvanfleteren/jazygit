package net.vanfleteren.jazygit.feature.stage;

import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Model;

import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.git.model.FileEntry;
import java.util.List;
import java.util.Optional;

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
    record Requested(List<FileEntry> files, Optional<String> directory) implements StageMsg {

        public Requested {
            files = List.copyOf(files);
        }

        public Requested(List<FileEntry> files) {
            this(files, Optional.empty());
        }
    }

    record Done(Action action, List<String> commands) implements StageMsg {

        public Done {
            commands = List.copyOf(commands);
        }
    }

    enum Action {
        STAGE, UNSTAGE
    }

    record Failed(String message) implements StageMsg {
    }
}
