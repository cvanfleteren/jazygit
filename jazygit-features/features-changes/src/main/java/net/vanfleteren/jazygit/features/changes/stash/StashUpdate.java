package net.vanfleteren.jazygit.features.changes.stash;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.operation.OperationUpdate;
import net.vanfleteren.jazygit.kernel.state.popup.PopupUpdate;

/**
 * Stashing the changes, and unstashing them again.
 */
public final class StashUpdate {

    private StashUpdate() {}

    public static Next update(Model model, StashMsg msg) {
        return switch (msg) {
            case StashMsg.Requested() -> requested(model);
            case StashMsg.PopRequested(var stash) when !listed(model, stash) -> Next.of(model);
            case StashMsg.PopRequested(var stash) -> OperationUpdate.start(model.clearError(), new StashCmd.Pop(stash));
            case StashMsg.DropRequested(var stash) ->
                PopupUpdate.ask(model, Option.when(listed(model, stash), () -> new StashDropPopup(stash)));
        };
    }

    /**
     * Asks for a name, when there are changes to stash.
     */
    private static Next requested(Model model) {
        return model.repo()
                .files()
                .map(files -> files.isEmpty()
                        ? Next.of(model.fail(new StashError.NothingToStash()))
                        : PopupUpdate.ask(model, new StashPopup()))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Only a stash that is listed is unstashed or deleted.
     */
    private static boolean listed(Model model, net.vanfleteren.jazygit.core.git.model.Stash stash) {
        return model.repo().stash(stash.sha()).isDefined();
    }
}
