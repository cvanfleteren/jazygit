package net.vanfleteren.jazygit.feature.github.review;

import io.vavr.collection.List;
import java.time.Duration;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.ui.TimeText;
import net.vanfleteren.jazygit.ui.widgets.ConfirmDialog;
import net.vanfleteren.jazygit.ui.widgets.MarkdownScroll;

/**
 * Asking to confirm deleting a comment of a review thread (ADR 64): who wrote it, when, and its first line, as GitHub
 * cannot bring it back.
 */
public final class DeleteCommentDialog {

    public static final ConfirmDialog.Spec<DeleteCommentPopup> SPEC = new ConfirmDialog.Spec<>(
            "delete-review-comment",
            DeleteCommentPopup.class,
            popup -> Messages.get("dialog.deleteComment.title"),
            DeleteCommentDialog::message,
            ReviewMsg.DeleteConfirmed::new);

    private DeleteCommentDialog() {}

    private static String message(DeleteCommentPopup popup) {
        String author = popup.comment().author().isEmpty()
                ? Messages.get("content.remarks.ghost")
                : popup.comment().author();
        return Messages.get(
                "dialog.deleteComment.message",
                author,
                TimeText.ago(Duration.between(popup.comment().at(), popup.now())),
                firstLine(popup.comment().body()));
    }

    /**
     * The first line of {@code body} with text, without its HTML comments, as a collapsed comment shows it.
     */
    private static String firstLine(String body) {
        return List.ofAll(MarkdownScroll.withoutHtmlComments(body).lines())
                .map(String::strip)
                .find(line -> !line.isEmpty())
                .getOrElse("");
    }
}
