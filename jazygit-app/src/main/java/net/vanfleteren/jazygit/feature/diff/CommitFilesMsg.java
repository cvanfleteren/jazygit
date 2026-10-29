package net.vanfleteren.jazygit.feature.diff;

import net.vanfleteren.jazygit.state.CommitFilesView;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

public sealed interface CommitFilesMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return CommitFilesUpdate.update(model, this);
    }

    /**
     * The user asked for the files {@code of} changed, from the pane {@code pane}.
     */
    record Opened(CommitFilesView.Changes of, String pane) implements CommitFilesMsg {}

    /**
     * The user closed the files of the commit or the stash, to see the files of the working tree again.
     */
    record Closed() implements CommitFilesMsg {}
}
