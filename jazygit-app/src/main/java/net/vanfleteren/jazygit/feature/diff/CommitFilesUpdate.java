package net.vanfleteren.jazygit.feature.diff;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.CommitFilesView;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Listing the files a commit or a stash changed in the files pane, in place of the changed files of the working
 * tree, and going back to those (ADR 44). The view moves the focus: into the files pane when they are listed, and back
 * to the pane they were opened from when they are closed.
 */
public final class CommitFilesUpdate {

    private CommitFilesUpdate() {}

    public static Next update(Model model, CommitFilesMsg msg) {
        return switch (msg) {
            case CommitFilesMsg.Opened(var of, var pane) -> {
                CommitFilesView view = CommitFilesView.of(of, pane, Scopes.FILES.id());
                yield Next.of(model.mapRepoView(repoView -> repoView.withCommitFiles(Option.of(view))), view.load());
            }
            case CommitFilesMsg.Closed() ->
                Next.of(model.mapRepoView(repoView -> repoView.withCommitFiles(Option.none())));
        };
    }
}
