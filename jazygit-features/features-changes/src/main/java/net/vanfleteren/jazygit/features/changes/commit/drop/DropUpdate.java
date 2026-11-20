package net.vanfleteren.jazygit.features.changes.commit.drop;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.core.repo.Commit;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.popup.PopupUpdate;

/**
 * Dropping a commit from the history of the checked out branch.
 */
public final class DropUpdate {

    private DropUpdate() {}

    public static Next update(Model model, DropMsg msg) {
        return switch (msg) {
            case DropMsg.Requested(Sha sha) -> requested(model, sha);
        };
    }

    private static Next requested(Model model, Sha sha) {
        return model.commit(sha)
                .filter(commit -> droppable(model, commit))
                .map(DropPopup::new)
                .transform(popup -> PopupUpdate.ask(model, popup));
    }

    /**
     * A merge can't be dropped, as the rebase would bring the merged commits back, and neither can the only
     * commit, which would leave the branch without any. The commits of the pane are all in the history of HEAD,
     * so a first commit that is not HEAD has commits following it, even one older than the log reads.
     */
    private static boolean droppable(Model model, Commit commit) {
        return switch (commit.parents().size()) {
            case 0 ->
                model.repo()
                        .commits()
                        .toOption()
                        .flatMap(List::headOption)
                        .exists(head -> !head.sha().equals(commit.sha()));
            case 1 -> true;
            default -> false;
        };
    }
}
