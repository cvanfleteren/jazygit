package net.vanfleteren.jazygit.feature.github.review;

import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking to confirm deleting a comment of a review thread of the diff of a pull request's files (ADR 64).
 *
 * @param number  the number of the pull request
 * @param thread  the id of the thread the comment is of
 * @param comment the comment, which the popup says who wrote, when, and the first line of
 * @param now     when it was asked, to tell how long ago the comment was written
 */
public record DeleteCommentPopup(int number, String thread, ReviewThread.Comment comment, Instant now)
        implements Popup {}
