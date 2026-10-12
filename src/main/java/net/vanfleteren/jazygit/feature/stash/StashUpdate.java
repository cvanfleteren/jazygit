package net.vanfleteren.jazygit.feature.stash;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.feature.stash.StashCmd.Stash;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Loadable.Loaded;
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
            case StashMsg.DropConfirmed() -> model.popup(StashDropPopup.class)
                    .map(popup -> Next.of(model.withoutPopup(StashDropPopup.class), new StashCmd.Drop(popup.ref())))
                    .getOrElse(() -> Next.of(model));
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
        return model.stashes() instanceof Loaded<List<net.vanfleteren.jazygit.git.model.Stash>>(var stashes)
                && stashes.exists(s -> s.ref().equals(ref));
    }

    private static Next confirmed(Model model, String name) {
        String trimmed = name.strip();
        return model.popup(StashPopup.class)
                .map(popup -> Next.of(model.withoutPopup(StashPopup.class).clearError(),
                        new Stash(trimmed.isEmpty() ? defaultName(model) : trimmed)))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * {@code WIP on <branch> <short sha> <message of HEAD>}, leaving out what is not known.
     */
    static String defaultName(Model model) {
        if (!(model.status() instanceof Loaded<RepoStatus>(RepoStatus status))) {
            return Messages.get("stash.defaultName", "").strip();
        }
        String sha = status.headCommit().map(Sha::shortSha).getOrElse("");
        String message = model.commits() instanceof Loaded<List<Commit>>(List<Commit> commits)
                ? commits.find(c -> status.headCommit().contains(c.sha())).map(Commit::message).getOrElse("")
                : "";
        return List.of(Messages.get("stash.defaultName", status.branchLabel()), sha, message)
                .filter(part -> !part.isEmpty())
                .mkString(" ");
    }
}
