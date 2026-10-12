package net.vanfleteren.jazygit.feature.commit;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.feature.commit.CommitCmd.Squash;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.CommandLog;
import net.vanfleteren.jazygit.state.Loadable.Loaded;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.Update;

import java.util.Optional;

/**
 * Squashing a commit into the one below it.
 */
public final class SquashUpdate {

    private SquashUpdate() {
    }

    public static Next update(Model model, SquashMsg msg) {
        return switch (msg) {
            case SquashMsg.Requested(int index) -> requested(model, index);
            case SquashMsg.Cancelled() -> Next.of(model.withoutPopup(SquashPopup.class));
            case SquashMsg.Confirmed() -> model.popup(SquashPopup.class)
                    .map(popup -> Next.of(model.withoutPopup(SquashPopup.class).withError(Optional.empty()),
                            new Squash(popup.commit().sha())))
                    .orElseGet(() -> Next.of(model));
            case SquashMsg.Done(List<String> commands) ->
                    Update.refresh(CommandLog.append(model.withError(Optional.empty()), "log.title.squash", commands));
            case SquashMsg.Failed(String message) ->
                    Update.refresh(model.withError(Optional.of(Messages.get("error.squash.failed", message))));
        };
    }

    /**
     * A commit with exactly one parent can be squashed into it: a root commit has nothing to squash into, and
     * a merge has no single commit below it.
     */
    private static Next requested(Model model, int index) {
        if (!(model.commits() instanceof Loaded<List<Commit>>(List<Commit> commits))
                || index < 0 || index >= commits.size()) {
            return Next.of(model);
        }
        Commit commit = commits.get(index);
        if (commit.parents().size() != 1) {
            return Next.of(model);
        }
        return commits.find(c -> c.sha().equals(commit.parents().head())).toJavaOptional()
                .map(into -> Next.of(model.withError(Optional.empty()).openPopup(new SquashPopup(commit, into))))
                .orElseGet(() -> Next.of(model));
    }
}
