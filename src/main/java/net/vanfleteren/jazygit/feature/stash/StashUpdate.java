package net.vanfleteren.jazygit.feature.stash;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.feature.stash.StashCmd.Stash;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.CommandLog;
import net.vanfleteren.jazygit.state.Loadable.Loaded;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.Update;

import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
            case StashMsg.Done(List<String> commands) ->
                    Update.refresh(CommandLog.append(model.withError(Optional.empty()), "log.title.stash", commands));
            case StashMsg.Failed(String message) ->
                    Update.refresh(model.withError(Optional.of(Messages.get("error.stash.failed", message))));
            case StashMsg.PopRequested(String ref) ->
                    listed(model, ref) ? Next.of(model.withError(Optional.empty()), new StashCmd.Pop(ref)) : Next.of(model);
            case StashMsg.DropRequested(String ref) ->
                    listed(model, ref) ? Next.of(model.withError(Optional.empty()).openPopup(new StashDropPopup(ref))) : Next.of(model);
            case StashMsg.DropCancelled() -> Next.of(model.withoutPopup(StashDropPopup.class));
            case StashMsg.DropConfirmed() -> model.popup(StashDropPopup.class)
                    .map(popup -> Next.of(model.withoutPopup(StashDropPopup.class), new StashCmd.Drop(popup.ref())))
                    .orElseGet(() -> Next.of(model));
            case StashMsg.Dropped(List<String> commands) ->
                    Update.refresh(CommandLog.append(model.withError(Optional.empty()), "log.title.stashDrop", commands));
            case StashMsg.DropFailed(String message) ->
                    Update.refresh(model.withError(Optional.of(Messages.get("error.stashDrop.failed", message))));
            case StashMsg.Popped(List<String> commands) ->
                    Update.refresh(CommandLog.append(model.withError(Optional.empty()), "log.title.stashPop", commands));
            case StashMsg.PopFailed(String message) ->
                    Update.refresh(model.withError(Optional.of(Messages.get("error.stashPop.failed", message))));
        };
    }

    /**
     * Asks for a name, when there are changes to stash.
     */
    private static Next requested(Model model) {
        return Update.files(model).isEmpty()
                ? Next.of(model.withError(Optional.of(Messages.get("error.stash.nothing"))))
                : Next.of(model.withError(Optional.empty()).openPopup(new StashPopup()));
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
                .map(popup -> Next.of(model.withoutPopup(StashPopup.class).withError(Optional.empty()),
                        new Stash(trimmed.isEmpty() ? defaultName(model) : trimmed)))
                .orElseGet(() -> Next.of(model));
    }

    /**
     * {@code WIP on <branch> <short sha> <message of HEAD>}, leaving out what is not known.
     */
    static String defaultName(Model model) {
        if (!(model.status() instanceof Loaded<RepoStatus>(RepoStatus status))) {
            return Messages.get("stash.defaultName", "").strip();
        }
        String sha = status.headOid().equals(RepoStatus.INITIAL)
                ? ""
                : status.headOid().substring(0, Math.min(7, status.headOid().length()));
        String message = model.commits() instanceof Loaded<List<Commit>>(List<Commit> commits)
                ? commits.find(c -> c.sha().value().equals(status.headOid())).map(Commit::message).getOrElse("")
                : "";
        return Stream.of(Messages.get("stash.defaultName", status.branchLabel()), sha, message)
                .filter(part -> !part.isEmpty())
                .collect(Collectors.joining(" "));
    }
}
