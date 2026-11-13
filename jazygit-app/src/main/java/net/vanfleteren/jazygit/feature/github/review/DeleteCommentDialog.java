package net.vanfleteren.jazygit.feature.github.review;

import io.vavr.collection.Iterator;
import java.time.Duration;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.ui.markdown.MarkdownImages;
import net.vanfleteren.jazygit.ui.markdown.MarkdownScroll;
import net.vanfleteren.jazygit.ui.text.TimeText;
import net.vanfleteren.jazygit.ui.widgets.ConfirmDialog;

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
     * The first line of {@code body} with text, without its HTML comments and with its images as text that says so,
     * as a collapsed comment shows it.
     */
    private static String firstLine(String body) {
        return Iterator.ofAll(MarkdownImages.asText(MarkdownScroll.withoutHtmlComments(body))
                        .lines()
                        .iterator())
                .map(String::strip)
                .find(line -> !line.isEmpty())
                .getOrElse("");
    }
}
