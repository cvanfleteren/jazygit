package net.vanfleteren.jazygit.feature.github.review;

import static net.vanfleteren.jazygit.feature.github.review.ReviewLookup.chosen;
import static net.vanfleteren.jazygit.feature.github.review.ReviewLookup.gone;
import static net.vanfleteren.jazygit.feature.github.review.ReviewLookup.mapPullRequest;
import static net.vanfleteren.jazygit.feature.github.review.ReviewLookup.refused;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.github.GitHubError.CannotReview.Why;
import net.vanfleteren.jazygit.feature.github.review.ReactCmd.React;
import net.vanfleteren.jazygit.gh.model.Reaction;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.diff.*;
import net.vanfleteren.jazygit.state.operation.*;

/**
 * Reacting to a comment of a review thread of the diff of a pull request's files, from its cursor (ADR 86): the
 * reaction is chosen in a popup, and given, or taken back when the viewer gave it. Reacting does not number a line: it
 * is done at whatever commits, on a merged pull request too. What GitHub did is known, so the threads are not read
 * again.
 */
public final class ReactUpdate {

    private ReactUpdate() {}

    public static Next update(Model model, ReactMsg msg) {
        return switch (msg) {
            case ReactMsg.ReactAsked(DiffCursorAt at) -> reactAsked(model, at);
            case ReactMsg.ReactionChosen(Reaction reaction) -> reactionChosen(model, reaction);
            case ReactMsg.Reacted(React cmd, var commands) -> reacted(model, cmd, commands);
            case ReactMsg.ReactFailed(React cmd, Failure failure) -> reactFailed(model, cmd, failure);
        };
    }

    /**
     * Reacting to the comment of the cursor {@code at} asked for: the reaction is chosen first. Reacting does not
     * number a line: it is done at whatever commits, on a merged pull request too (ADR 86).
     */
    private static Next reactAsked(Model model, DiffCursorAt at) {
        return reactable(model, at.cursor())
                .fold(
                        why -> Next.of(refused(model, why)),
                        on -> Update.ask(
                                model,
                                new ReactionPopup(
                                        on.number(),
                                        on.thread().id(),
                                        on.comment().id(),
                                        on.comment().reactions())));
    }

    /**
     * {@code reaction} chosen in the popup, if it is open.
     */
    private static Next reactionChosen(Model model, Reaction reaction) {
        return model.popup(ReactionPopup.class)
                .map(popup -> confirmReaction(model, popup, reaction))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * {@code cmd} reacted to its comment. Not read again: what GitHub did is known, as for an edit (ADR 65).
     */
    private static Next reacted(Model model, React cmd, List<ExecutedCommand> commands) {
        return OperationUpdate.succeeded(model, cmd, commands)
                .map(done -> mapPullRequest(
                        done,
                        cmd.number(),
                        view -> view.withReacted(cmd.comment(), cmd.reaction(), cmd.given())
                                .withReacting(view.reacting().remove(cmd.comment()))));
    }

    /**
     * {@code cmd} failed to react to its comment. A comment GitHub no longer has was deleted, as by someone else: it
     * leaves the threads, as one deleted here does (ADR 64), and the failure says so.
     */
    private static Next reactFailed(Model model, React cmd, Failure failure) {
        Next failed = OperationUpdate.update(model, new OperationMsg.Failed(cmd, failure))
                .map(done -> mapPullRequest(
                        done,
                        cmd.number(),
                        view -> view.withReacting(view.reacting().remove(cmd.comment()))));
        return failure.gone() ? failed.then(done -> gone(done, cmd.number(), cmd.thread(), cmd.comment())) : failed;
    }

    /**
     * The comment the cursor {@code on} is on, with its thread and the files listed, if the viewer may react to it now
     * (ADR 86): chosen as for an edit or a delete, and not while a reaction to it is sent, which a second one, chosen
     * from what was read before, could undo.
     */
    private static Either<Why, OnComment> reactable(Model model, Anchor on) {
        return chosen(model, on)
                .filterOrElse(found -> found.comment().rights().react(), found -> Why.NO_REACT)
                .filterOrElse(found -> !found.reacting(), found -> Why.REACTING);
    }

    /**
     * {@code reaction} chosen in {@code popup}: asked again as the comment is now, as the threads may have been read
     * again, the comment gone, or something be sent for it meanwhile. Whether it is given or taken back is told from
     * the reactions it has now, not those the popup opened with.
     */
    private static Next confirmReaction(Model model, ReactionPopup popup, Reaction reaction) {
        Anchor on = new Anchor.Comment(popup.comment(), new Anchor.Thread(popup.thread(), Option.none()));
        return reactable(model, on)
                .filterOrElse(found -> found.number().equals(popup.number()), found -> Why.NOT_READ)
                .fold(why -> Next.of(refused(model.withoutPopup(ReactionPopup.class), why)), found -> {
                    React react = new React(
                            popup.number(),
                            popup.thread(),
                            popup.comment(),
                            reaction,
                            !found.comment().reactions().viewerGave(reaction));
                    return Update.confirm(model, ReactionPopup.class, open -> Option.of(react))
                            .map(started -> mapPullRequest(
                                    started,
                                    popup.number(),
                                    view -> view.withReacting(view.reacting().add(popup.comment()))));
                });
    }
}
