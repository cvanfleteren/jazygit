package net.vanfleteren.jazygit.feature.commit;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.commit.CommitCmd.Drop;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Dropping a commit from the history of the checked out branch.
 */
public final class DropUpdate {

    private DropUpdate() {}

    public static Next update(Model model, DropMsg msg) {
        return switch (msg) {
            case DropMsg.Requested(Sha sha) -> requested(model, sha);
            case DropMsg.Cancelled() -> Next.of(model.withoutPopup(DropPopup.class));
            case DropMsg.Confirmed() ->
                Update.confirm(
                        model,
                        DropPopup.class,
                        popup -> Option.of(new Drop(popup.commit().sha())));
        };
    }

    private static Next requested(Model model, Sha sha) {
        return model.repo()
                .commit(sha)
                .filter(commit -> droppable(model, commit))
                .map(DropPopup::new)
                .transform(popup -> Update.ask(model, popup));
    }

    /**
     * A merge can't be dropped, as the rebase would bring the merged commits back, and neither can the only
     * commit, which would leave the branch without any.
     */
    private static boolean droppable(Model model, Commit commit) {
        return switch (commit.parents().size()) {
            case 0 ->
                model.repo()
                        .commits()
                        .toOption()
                        .exists(log -> log.exists(other -> other.parents().contains(commit.sha())));
            case 1 -> true;
            default -> false;
        };
    }
}
