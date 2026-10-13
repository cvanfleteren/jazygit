package net.vanfleteren.jazygit.feature.stash;

import io.vavr.control.Option;
import io.vavr.collection.List;
import net.vanfleteren.jazygit.feature.stash.StashCmd.Stash;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.Update;

/**
 * Stashing the changes, and unstashing them again.
 */
public final class StashUpdate {

    private StashUpdate() {
    }

    public static Next update(Model model, StashMsg msg) {
        return switch (msg) {
            case StashMsg.Requested() -> requested(model);
            case StashMsg.Cancelled() -> Next.of(model.withoutPopup(StashPopup.class));
            case StashMsg.Confirmed(String name) -> confirmed(model, name);
            case StashMsg.PopRequested(String ref) ->
                    listed(model, ref) ? Next.of(model.clearError(), new StashCmd.Pop(ref)) : Next.of(model);
            case StashMsg.DropRequested(String ref) ->
                    listed(model, ref) ? Next.of(model.clearError().openPopup(new StashDropPopup(ref))) : Next.of(model);
            case StashMsg.DropCancelled() -> Next.of(model.withoutPopup(StashDropPopup.class));
            case StashMsg.DropConfirmed() ->
                    Update.confirm(model, StashDropPopup.class, popup -> Option.of(new StashCmd.Drop(popup.ref())));
        };
    }

    /**
     * Asks for a name, when there are changes to stash.
     */
    private static Next requested(Model model) {
        return Update.files(model).isEmpty()
                ? Next.of(model.fail(new AppError.NothingToStash()))
                : Next.of(model.clearError().openPopup(new StashPopup()));
    }

    /**
     * Only a stash that is listed is unstashed or deleted.
     */
    private static boolean listed(Model model, String ref) {
        return model.stashes().getOrElse(List.empty()).exists(stash -> stash.ref().equals(ref));
    }

    private static Next confirmed(Model model, String name) {
        String trimmed = name.strip();
        return Update.confirm(model, StashPopup.class,
                popup -> Option.of(new Stash(Option.of(trimmed).filter(text -> !text.isEmpty()))));
    }
}
