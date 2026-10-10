package net.vanfleteren.jazygit.feature.stage;

import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Model;

import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.git.model.FileEntry;
import java.util.List;

public sealed interface StageMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return StageUpdate.update(model, this);
    }

    /**
     * The user asked to stage the given files, or to unstage them if they are all staged already.
     */
    record Requested(List<FileEntry> files) implements StageMsg {

        public Requested {
            files = List.copyOf(files);
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
