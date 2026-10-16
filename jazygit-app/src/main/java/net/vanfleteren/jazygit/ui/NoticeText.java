package net.vanfleteren.jazygit.ui;

import net.vanfleteren.jazygit.git.model.InProgress;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Notice;

/**
 * Describes notices to the user. The only place that turns them into text.
 */
public final class NoticeText {

    private NoticeText() {}

    public static String of(Notice notice) {
        return switch (notice) {
            case Notice.StoppedOnConflicts(var inProgress, int conflicts) ->
                inProgress
                        .map(op -> stopped(op, conflicts))
                        .getOrElse(() -> Messages.get("notice.stopped.uncommitted", conflicts));
        };
    }

    /**
     * What to do with {@code inProgress}, which stopped on {@code conflicts} conflicts: a rebase says how far it
     * got, once git wrote that down.
     */
    private static String stopped(InProgress inProgress, int conflicts) {
        return switch (inProgress) {
            case InProgress.Rebasing(var branch, var onto, int step, int total)
            when total > 0 -> Messages.get("notice.stopped.rebase", step, total, conflicts);
            default ->
                Messages.get(
                        inProgress.skippable() ? "notice.stopped.skippable" : "notice.stopped",
                        InProgressText.kind(inProgress),
                        conflicts);
        };
    }
}
