package net.vanfleteren.jazygit.feature.stash;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.stash.StashCmd.Stash;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Stashing the changes, and unstashing them again.
 */
public final class StashUpdate {

    private StashUpdate() {}

    public static Next update(Model model, StashMsg msg) {
        return switch (msg) {
            case StashMsg.Requested() -> requested(model);
            case StashMsg.Cancelled() -> Next.of(model.withoutPopup(StashPopup.class));
            case StashMsg.Confirmed(String name) -> confirmed(model, name);
            case StashMsg.PopRequested(var stash) ->
                listed(model, stash) ? Update.start(model.clearError(), new StashCmd.Pop(stash)) : Next.of(model);
            case StashMsg.DropRequested(var stash) ->
                Update.ask(model, Option.when(listed(model, stash), () -> new StashDropPopup(stash)));
            case StashMsg.DropCancelled() -> Next.of(model.withoutPopup(StashDropPopup.class));
            case StashMsg.DropConfirmed() ->
                Update.confirm(model, StashDropPopup.class, popup -> Option.of(new StashCmd.Drop(popup.stash())));
        };
    }

    /**
     * Asks for a name, when there are changes to stash.
     */
    private static Next requested(Model model) {
        return Update.withFiles(
                model,
                files -> files.isEmpty()
                        ? Next.of(model.fail(new AppError.NothingToStash()))
                        : Update.ask(model, new StashPopup()));
    }

    /**
     * Only a stash that is listed is unstashed or deleted.
     */
    private static boolean listed(Model model, net.vanfleteren.jazygit.git.model.Stash stash) {
        return model.stashes()
                .getOrElse(List.empty())
                .exists(listed -> listed.sha().equals(stash.sha()));
    }

    private static Next confirmed(Model model, String name) {
        String trimmed = name.strip();
        return Update.confirm(
                model,
                StashPopup.class,
                popup -> Option.of(new Stash(Option.of(trimmed).filter(text -> !text.isEmpty()))));
    }
}
