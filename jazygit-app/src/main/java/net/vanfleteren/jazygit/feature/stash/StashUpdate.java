package net.vanfleteren.jazygit.feature.stash;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.OperationUpdate;
import net.vanfleteren.jazygit.state.Update.Next;

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
                OperationUpdate.ask(model, Option.when(listed(model, stash), () -> new StashDropPopup(stash)));
        };
    }

    /**
     * Asks for a name, when there are changes to stash.
     */
    private static Next requested(Model model) {
        return model.repo()
                .files()
                .map(files -> files.isEmpty()
                        ? Next.of(model.fail(new AppError.NothingToStash()))
                        : OperationUpdate.ask(model, new StashPopup()))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Only a stash that is listed is unstashed or deleted.
     */
    private static boolean listed(Model model, net.vanfleteren.jazygit.git.model.Stash stash) {
        return model.repo()
                .stashes()
                .getOrElse(List.empty())
                .exists(listed -> listed.sha().equals(stash.sha()));
    }
}
