package net.vanfleteren.jazygit.feature.inprogress;

import net.vanfleteren.jazygit.git.model.InProgressAction;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Continuing, aborting or skipping the operation the repository stopped in the middle of: a merge, a rebase, a
 * cherry-pick or a revert.
 */
public final class InProgressUpdate {

    private InProgressUpdate() {}

    public static Next update(Model model, InProgressMsg msg) {
        return switch (msg) {
            case InProgressMsg.Requested() -> requested(model);
            case InProgressMsg.Chosen(InProgressAction action) -> chosen(model, action);
        };
    }

    /**
     * With nothing in progress, nothing happens.
     */
    private static Next requested(Model model) {
        return Update.ask(
                model,
                model.repo()
                        .status()
                        .toOption()
                        .flatMap(status -> status.inProgress()
                                .map(inProgress -> new InProgressPopup(inProgress, status.conflicts()))));
    }

    /**
     * Continuing starts right away, but is refused while conflicts remain, as git would. Aborting and skipping
     * throw work away, so they are confirmed first. A merge cannot be skipped.
     */
    private static Next chosen(Model model, InProgressAction action) {
        return model.popup(InProgressPopup.class)
                .filter(popup ->
                        action != InProgressAction.SKIP || popup.inProgress().skippable())
                .map(popup -> {
                    Model closed = model.withoutPopup(InProgressPopup.class);
                    return action != InProgressAction.CONTINUE
                            ? Update.ask(closed, new InProgressConfirmPopup(popup.inProgress(), action))
                            : popup.conflicts() > 0
                                    ? Next.of(closed.fail(AppError.refused("error.conflictsRemain", popup.conflicts())))
                                    : Update.start(closed.clearError(), new InProgressCmd(popup.inProgress(), action));
                })
                .getOrElse(() -> Next.of(model));
    }
}
