package net.vanfleteren.jazygit.feature.github.review;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.gh.model.Reaction;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog.Choice;

/**
 * Popup asking which reaction to give to a comment of a review thread, as GitHub's site offers them, each chosen by
 * its number (ADR 86). One the viewer gave already says so, and choosing it takes it back.
 */
public final class ReactionDialog {

    public static final ChoiceDialog.Spec<ReactionPopup> SPEC = new ChoiceDialog.Spec<>(
            "review-comment-reaction",
            ReactionPopup.class,
            popup -> Messages.get("dialog.reaction.title"),
            List.of(Reaction.values()).zipWithIndex().map(each -> choice(each._1(), each._2())));

    private ReactionDialog() {}

    private static Choice<ReactionPopup> choice(Reaction reaction, int index) {
        return new Choice<>(
                Character.forDigit(index + 1, 10),
                popup -> label(popup, reaction),
                () -> new ReactMsg.ReactionChosen(reaction));
    }

    private static String label(ReactionPopup popup, Reaction reaction) {
        String name = Messages.get("dialog.reaction." + reaction.name());
        return popup.reactions().viewerGave(reaction)
                ? Messages.get("dialog.reaction.given", reaction.emoji(), name)
                : Messages.get("dialog.reaction.option", reaction.emoji(), name);
    }
}
