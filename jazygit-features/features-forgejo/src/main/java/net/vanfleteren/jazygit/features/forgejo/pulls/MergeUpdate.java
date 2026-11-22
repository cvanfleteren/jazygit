package net.vanfleteren.jazygit.features.forgejo.pulls;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.forgejo.ForgejoMergeSettings;
import net.vanfleteren.jazygit.core.forgejo.ForgejoMergeStyle;
import net.vanfleteren.jazygit.core.forgejo.ForgejoMerged;
import net.vanfleteren.jazygit.core.forgejo.ForgejoPullRequestDetails;
import net.vanfleteren.jazygit.features.changes.fetch.FetchUpdate;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.operation.OperationMsg;
import net.vanfleteren.jazygit.kernel.state.operation.OperationUpdate;
import net.vanfleteren.jazygit.kernel.state.popup.PopupUpdate;

/**
 * Merging a pull request on Forgejo from its dialog (ADR 180), which Forgejo does at once: the dialog closes, as on
 * GitHub (ADR 24), and the merge is an operation on the branch shown. Once merged, the remotes are fetched and the
 * pull requests read again; a merge that failed is shown as any failed operation, and kept in Forgejo's part.
 */
final class MergeUpdate {

    private MergeUpdate() {}

    /**
     * The user chose to merge the pull request shown as {@code style}: merged, when its key merges it now.
     */
    static Next chosen(Model model, ForgejoMergeStyle style) {
        return PopupUpdate.confirm(
                model,
                ForgejoPullRequestPopup.class,
                popup -> Option.when(!popup.reading(), popup)
                        .flatMap(ForgejoPullRequestPopup::shown)
                        .filter(shown -> MergeKey.of(shown, style) instanceof MergeKey.Now)
                        .map(shown -> cmd(popup, shown, style)));
    }

    private static MergePullRequestCmd cmd(
            ForgejoPullRequestPopup popup, ShownPullRequest shown, ForgejoMergeStyle style) {
        ForgejoPullRequestDetails pr = shown.pullRequest();
        return new MergePullRequestCmd(
                popup.home(),
                popup.local(),
                pr.number(),
                style,
                new FailedMerge.Standing(pr.branches().headSha()),
                shown.settings().exists(ForgejoMergeSettings::deleteBranch));
    }

    /**
     * Whether the key of {@code style} merges the pull request {@code popup} shows now.
     */
    static boolean choosable(ForgejoPullRequestPopup popup, ForgejoMergeStyle style) {
        return !popup.reading() && popup.shown().exists(shown -> MergeKey.of(shown, style) instanceof MergeKey.Now);
    }

    /**
     * Forgejo merged the pull request of {@code cmd}: done like any operation, a merge of it that failed before is
     * forgotten, and the remotes are fetched, as its base changed. A head branch that was kept although it was to be
     * deleted is told.
     */
    static Next merged(Model model, MergePullRequestCmd cmd, ForgejoMerged merged) {
        Next done = OperationUpdate.succeeded(model, cmd, List.empty())
                .map(after -> after.mapPart(ForgejoPullRequests.PART, prs -> prs.merged(cmd.number())));
        return FetchUpdate.requested(
                switch (merged) {
                    case ForgejoMerged.Merged() -> done;
                    case ForgejoMerged.BranchKept(String why) ->
                        done.map(after -> after.inform(new BranchKeptNotice(cmd.branch(), why)));
                });
    }

    /**
     * Merging the pull request of {@code cmd} failed as {@code failure} says: shown as any failed operation, and kept
     * as its last merge that failed. The pull requests are read again: its head may have moved on, or it may be merged
     * all the same, as Forgejo merges before it deletes the branch, and a deletion that fails otherwise than the 403s
     * known is answered as a failure.
     */
    static Next failed(Model model, MergePullRequestCmd cmd, Failure failure) {
        return OperationUpdate.update(model, new OperationMsg.Failed(cmd, failure))
                .map(after -> after.mapPart(
                        ForgejoPullRequests.PART,
                        prs -> prs.mergeFailed(new FailedMerge(cmd.number(), cmd.style(), failure, cmd.standing()))))
                .and(new ReadPullRequests(cmd.home()));
    }
}
