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
 * Merging a pull request on Forgejo from its dialog (ADR 180, 181), as an operation on the branch shown. Merging it
 * now closes the dialog, as on GitHub (ADR 24); once merged, the remotes are fetched and the pull requests read again.
 * Scheduling it for when its checks succeed, or cancelling that, keeps the dialog open, which reads it again once
 * done, as GitHub's does for auto-merge (ADR 112). A merge that failed is shown as any failed operation, and kept in
 * Forgejo's part.
 */
final class MergeUpdate {

    private MergeUpdate() {}

    /**
     * The user chose to merge the pull request shown as {@code style}: what its key does, while it is not being read.
     */
    static Next chosen(Model model, ForgejoMergeStyle style) {
        return model.popup(ForgejoPullRequestPopup.class)
                .filter(popup -> !popup.reading())
                .flatMap(popup -> popup.shown().map(shown -> switch (MergeKey.of(shown, style)) {
                    case MergeKey.Now() ->
                        PopupUpdate.confirm(
                                model, ForgejoPullRequestPopup.class, _ -> Option.of(cmd(popup, shown, style)));
                    case MergeKey.WhenChecksSucceed() ->
                        OperationUpdate.start(model.clearError(), cmd(popup, shown, style));
                    case MergeKey.Cancel(var merge) ->
                        OperationUpdate.start(
                                model.clearError(),
                                new CancelScheduledMergeCmd(
                                        popup.home(),
                                        popup.local(),
                                        shown.pullRequest().number(),
                                        merge.at()));
                    case MergeKey.Disabled(var _) -> Next.of(model);
                }))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Merging the pull request {@code shown} as {@code style}: when its checks succeed when its base branch does not
     * let it be merged yet.
     */
    private static MergePullRequestCmd cmd(
            ForgejoPullRequestPopup popup, ShownPullRequest shown, ForgejoMergeStyle style) {
        ForgejoPullRequestDetails pr = shown.pullRequest();
        return new MergePullRequestCmd(
                popup.home(),
                popup.local(),
                pr.number(),
                style,
                new FailedMerge.Standing(pr.branches().headSha()),
                shown.settings().exists(ForgejoMergeSettings::deleteBranch),
                shown.rules().filter(_ -> shown.waits()),
                shown.approvals());
    }

    /**
     * Whether the key of {@code style} does something to the pull request {@code popup} shows now.
     */
    static boolean choosable(ForgejoPullRequestPopup popup, ForgejoMergeStyle style) {
        return !popup.reading()
                && popup.shown().exists(shown -> !(MergeKey.of(shown, style) instanceof MergeKey.Disabled));
    }

    /**
     * Forgejo merged the pull request of {@code cmd}, or scheduled its merge, as {@code merged} says: done like any
     * operation, and a merge of it that failed before is forgotten. Once merged, the remotes are fetched, as its base
     * changed, and a head branch that was kept although it was to be deleted is told; once scheduled, its style is
     * kept. The dialog that still shows it reads it again.
     */
    static Next merged(Model model, MergePullRequestCmd cmd, ForgejoMerged merged) {
        Next done = OperationUpdate.succeeded(model, cmd, List.empty());
        Next went = switch (merged) {
            case ForgejoMerged.Merged() -> FetchUpdate.requested(done.map(after -> merged(after, cmd)));
            case ForgejoMerged.BranchKept(String why) ->
                FetchUpdate.requested(
                        done.map(after -> merged(after, cmd).inform(new BranchKeptNotice(cmd.branch(), why))));
            case ForgejoMerged.Scheduled() ->
                done.map(after ->
                        after.mapPart(ForgejoPullRequests.PART, prs -> prs.mergeScheduled(cmd.number(), cmd.style())));
        };
        return PullRequestUpdate.readAgain(went, cmd.number());
    }

    private static Model merged(Model model, MergePullRequestCmd cmd) {
        return model.mapPart(ForgejoPullRequests.PART, prs -> prs.merged(cmd.number()));
    }

    /**
     * Merging the pull request of {@code cmd} failed as {@code failure} says: shown as any failed operation, and kept
     * as its last merge that failed. The pull requests are read again: its head may have moved on, or it may be merged
     * all the same, as Forgejo merges before it deletes the branch, and a deletion that fails otherwise than the 403s
     * known is answered as a failure. The dialog that still shows it, as when it was to be merged when its checks
     * succeed, reads it again.
     */
    static Next failed(Model model, MergePullRequestCmd cmd, Failure failure) {
        Next shown = OperationUpdate.update(model, new OperationMsg.Failed(cmd, failure))
                .map(after -> after.mapPart(
                        ForgejoPullRequests.PART,
                        prs -> prs.mergeFailed(new FailedMerge(cmd.number(), cmd.style(), failure, cmd.standing()))));
        return PullRequestUpdate.readAgain(shown.and(new ReadPullRequests(cmd.home())), cmd.number());
    }

    /**
     * Forgejo did not schedule the merge of {@code cmd}, as one was scheduled already: no failure, as what was asked
     * for is so; it is told, and the dialog reads the pull request again, to show it.
     */
    static Next alreadyScheduled(Model model, MergePullRequestCmd cmd) {
        return PullRequestUpdate.readAgain(
                OperationUpdate.succeeded(model, cmd, List.empty())
                        .map(done -> done.inform(new ScheduleNotice.AlreadyScheduled(cmd.branch()))),
                cmd.number());
    }

    /**
     * The scheduled merge of the pull request of {@code cmd} was cancelled, or was not scheduled any more when
     * {@code gone}, which is told: no failure, as what was asked for is so; the timeline still says it is then, which
     * the dialog no longer shows. The dialog reads it again.
     */
    static Next cancelled(Model model, CancelScheduledMergeCmd cmd, boolean gone) {
        Next done = OperationUpdate.succeeded(model, cmd, List.empty())
                .map(after -> after.mapPart(
                        ForgejoPullRequests.PART,
                        prs -> gone ? prs.dropped(cmd.number(), cmd.scheduledAt()) : prs.unscheduled(cmd.number())));
        return PullRequestUpdate.readAgain(
                gone ? done.map(after -> after.inform(new ScheduleNotice.NoLongerScheduled(cmd.branch()))) : done,
                cmd.number());
    }
}
