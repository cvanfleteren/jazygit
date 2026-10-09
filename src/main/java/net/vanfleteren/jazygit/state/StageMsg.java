package net.vanfleteren.jazygit.state;

import net.vanfleteren.jazygit.git.model.FileEntry;

import java.util.List;

public sealed interface StageMsg extends Msg {

    /**
     * The user asked to stage the given files, or to unstage them if they are all staged already.
     */
    record Requested(List<FileEntry> files) implements StageMsg {

        public Requested {
            files = List.copyOf(files);
        }
    }

    record Done() implements StageMsg {
    }

    record Failed(String message) implements StageMsg {
    }
}
