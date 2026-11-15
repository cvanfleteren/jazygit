package net.vanfleteren.jazygit.github.review;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.changes.diff.CommitFilesMsg;
import net.vanfleteren.jazygit.github.GitHubError.CannotReview.Why;
import net.vanfleteren.jazygit.state.diff.DiffCursorAt;
import net.vanfleteren.jazygit.ui.FeatureAction;
import net.vanfleteren.jazygit.ui.PopupSlot;
import net.vanfleteren.jazygit.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.ui.keys.Scopes;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog;
import net.vanfleteren.jazygit.ui.widgets.ConfirmDialog;

/**
 * Commenting on the lines of the diff of a pull request's files from its cursor (ADR 60): the keys of that diff, the
 * popup that writes or edits a comment (ADR 65), the one that asks to delete one (ADR 64), and the one that picks a
 * reaction to one (ADR 86).
 */
public final class ReviewAction {

    public static final FeatureAction ACTION = new FeatureAction(
            keys -> keys.add(
                    Scopes.PULL_REQUEST_DIFF,
                    Binding.onSelected("c", "github.help.pullRequestDiff.comment", ReviewMsg.StartAsked::new),
                    Binding.onSelected("r", "github.help.pullRequestDiff.reply", ReviewMsg.ReplyAsked::new),
                    Binding.onSelected("x", "github.help.pullRequestDiff.resolve", ReviewMsg.ResolveAsked::new),
                    Binding.onSelected(" ", "github.help.pullRequestDiff.toggle", ThreadMsg.ThreadToggled::new),
                    Binding.onSelected("e", "github.help.pullRequestDiff.edit", ReviewMsg.EditAsked::new),
                    Binding.onSelected("d", "github.help.pullRequestDiff.delete", DeleteCommentMsg.DeleteAsked::new),
                    Binding.onSelected("o", "github.help.pullRequestDiff.open", ThreadMsg.OpenAsked::new),
                    Binding.onSelected("+", "github.help.pullRequestDiff.react", ReactMsg.ReactAsked::new),
                    new Binding<DiffCursorAt>(
                            "v",
                            "github.help.pullRequestDiff.range",
                            at -> at.map(
                                    cursor -> cursor.marked().getOrElse(() -> new ReviewMsg.Refused(Why.NOT_ON_LINE)))),
                    new Binding<DiffCursorAt>(
                            "n", "github.help.pullRequestDiff.next", at -> at.flatMap(DiffCursorAt::next)),
                    new Binding<DiffCursorAt>(
                            "N", "github.help.pullRequestDiff.previous", at -> at.flatMap(DiffCursorAt::previous)),
                    Binding.always("R", "changes.help.commitFiles.refresh", CommitFilesMsg.RemarksRefreshed::new)),
            dispatch -> List.of(
                    PopupSlot.returningToFocus(new ReviewDialog(dispatch)),
                    PopupSlot.returningToFocus(new ConfirmDialog<>(DeleteCommentDialog.SPEC, dispatch)),
                    PopupSlot.returningToFocus(new ChoiceDialog<>(ReactionDialog.SPEC, dispatch))));

    private ReviewAction() {}
}
