package net.vanfleteren.jazygit.feature.branch;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.BranchCmd.Merge;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.MergeMode;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.CommandLog;
import net.vanfleteren.jazygit.state.Loadable.Loaded;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Merging a branch into the checked out one.
 */
public final class MergeUpdate {

    private MergeUpdate() {
    }

    public static Next update(Model model, MergeMsg msg) {
        return switch (msg) {
            case MergeMsg.Requested(String branch) -> requested(model, branch);
            case MergeMsg.Cancelled() -> Next.of(model.withoutPopup(MergePopup.class));
            case MergeMsg.Chosen(MergeMode mode) -> model.popup(MergePopup.class).map(MergePopup::branch)
                    .map(branch -> Next.of(model.withoutPopup(MergePopup.class).withError(Option.none()),
                            new Merge(branch, mode)))
                    .getOrElse(() -> Next.of(model));
            // Status, branches and commits show the merge; a squash only changes the status.
            case MergeMsg.Done(String branch, List<String> commands) ->
                    Update.refresh(CommandLog.append(model.withError(Option.none()), "log.title.merge", commands));
            case MergeMsg.Failed(String branch, String message) ->
                    Update.refresh(model.withError(Option.of(Messages.get("error.merge.failed", branch, message))));
        };
    }

    /**
     * Only a known branch other than the checked out one can be merged, and only into a checked out branch.
     */
    private static Next requested(Model model, String branch) {
        if (!(model.branches() instanceof Loaded<List<Branch>>(List<Branch> branches))) {
            return Next.of(model);
        }
        Option<Branch> current = branches.find(Branch::current);
        boolean known = branches.exists(b -> b.name().equals(branch) && !b.current());
        return known && current.isDefined()
                ? Next.of(model.openPopup(new MergePopup(current.get().name(), branch)).withError(Option.none()))
                : Next.of(model);
    }
}
