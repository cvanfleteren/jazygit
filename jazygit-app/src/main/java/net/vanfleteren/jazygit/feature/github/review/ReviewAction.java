package net.vanfleteren.jazygit.feature.github.review;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.feature.FeatureAction;
import net.vanfleteren.jazygit.feature.diff.CommitFilesMsg;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.AppError.CannotReview.Why;
import net.vanfleteren.jazygit.state.DiffCursorAt;
import net.vanfleteren.jazygit.ui.PopupSlot;

/**
 * Commenting on the lines of the diff of a pull request's files from its cursor (ADR 60): the keys of that diff, and
 * the popup that writes a comment.
 */
public final class ReviewAction {

    public static final FeatureAction ACTION = new FeatureAction(
            keys -> keys.add(
                    Scopes.PULL_REQUEST_DIFF,
                    Binding.onSelected("c", "help.pullRequestDiff.comment", ReviewMsg.StartAsked::new),
                    Binding.onSelected("r", "help.pullRequestDiff.reply", ReviewMsg.ReplyAsked::new),
                    Binding.onSelected("x", "help.pullRequestDiff.resolve", ReviewMsg.ResolveAsked::new),
                    Binding.onSelected(" ", "help.pullRequestDiff.toggle", ReviewMsg.ThreadToggled::new),
                    new Binding<DiffCursorAt>(
                            "v",
                            "help.pullRequestDiff.range",
                            at -> at.map(
                                    cursor -> cursor.marked().getOrElse(() -> new ReviewMsg.Refused(Why.NOT_ON_LINE)))),
                    new Binding<DiffCursorAt>("n", "help.pullRequestDiff.next", at -> at.flatMap(DiffCursorAt::next)),
                    new Binding<DiffCursorAt>(
                            "N", "help.pullRequestDiff.previous", at -> at.flatMap(DiffCursorAt::previous)),
                    Binding.always("R", "help.commitFiles.refresh", CommitFilesMsg.RemarksRefreshed::new)),
            dispatch -> List.of(PopupSlot.returningToFocus(new ReviewDialog(dispatch))));

    private ReviewAction() {}
}
