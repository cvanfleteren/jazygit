package net.vanfleteren.jazygit.feature.github.review;

import net.vanfleteren.jazygit.gh.model.PrNumber;
import net.vanfleteren.jazygit.gh.model.Reactions;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.state.popup.Popup;

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
