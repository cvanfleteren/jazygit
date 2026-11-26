package net.vanfleteren.jazygit.features.github.review;

import net.vanfleteren.jazygit.core.gh.model.Reactions;
import net.vanfleteren.jazygit.core.gh.model.ReviewThread;
import net.vanfleteren.jazygit.core.repo.PrNumber;
import net.vanfleteren.jazygit.kernel.state.popup.Popup;

/**
 * Asking which reaction to give to a comment of a review thread of the diff of a pull request's files, or to take back
 * (ADR 86).
 *
 * @param number    the number of the pull request
 * @param thread    the id of the thread the comment is of
 * @param comment   the node id of the comment
 * @param reactions the reactions it has, which say which the viewer gave, and so takes back when chosen
 */
public record ReactionPopup(
        PrNumber number, ReviewThread.Id thread, ReviewThread.Comment.Id comment, Reactions reactions)
        implements Popup {}
