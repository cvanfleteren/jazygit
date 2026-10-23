package net.vanfleteren.jazygit.feature.start;

import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

/**
 * What happens while jazygit, started outside a repository, asks what to open instead (ADR 28).
 */
public sealed interface StartMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return StartUpdate.update(model, this);
    }

    /**
     * The user chose to make the folder a repository.
     */
    record InitChosen() implements StartMsg {}

    /**
     * The user chose to type the path of another folder.
     */
    record OpenFolderChosen() implements StartMsg {}

    /**
     * The user chose to pick one of the recent repositories.
     */
    record OpenRecentChosen() implements StartMsg {}

    record FolderConfirmed() implements StartMsg {}

    record FolderCancelled() implements StartMsg {}

    record RecentPicked(String directory) implements StartMsg {}

    record RecentCancelled() implements StartMsg {}

    /**
     * The repository whose working tree is at {@code worktree} opened; the app stops asking, and shows it.
     */
    record Opened(String worktree) implements StartMsg {}

    /**
     * The folder {@code directory} is in no repository.
     *
     * @param exists whether it is a folder at all
     */
    record NoRepository(String directory, boolean exists) implements StartMsg {}

    /**
     * What was chosen failed.
     */
    record Failed(Failure failure) implements StartMsg {}
}
