package net.vanfleteren.jazygit.feature.github.prototype;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.github.show.PullRequestPopup;
import net.vanfleteren.jazygit.gh.model.PrototypeComment;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * PROTOTYPE: showing the comments of the pull request that is shown.
 */
public sealed interface CommentsPrototypeMsg extends Msg {

    @Override
    default Next apply(Model model) {
        return switch (this) {
            case Requested() ->
                model.popup(PullRequestPopup.class)
                        .map(shown -> Next.of(
                                model.openPopup(new CommentsPrototypePopup(shown, Option.none())),
                                new CommentsPrototypeCmd.LoadComments(
                                        shown.pullRequest().number())))
                        .getOrElse(() -> Next.of(model));
            case Loaded(int number, var read) ->
                Next.of(model.popup(CommentsPrototypePopup.class)
                        .filter(popup -> popup.number() == number)
                        .map(popup -> model.openPopup(new CommentsPrototypePopup(popup.shown(), Option.of(read))))
                        .getOrElse(model));
            case Closed() ->
                Next.of(model.popup(CommentsPrototypePopup.class)
                        .map(popup -> model.openPopup(popup.shown()))
                        .getOrElse(model));
        };
    }

    record Requested() implements CommentsPrototypeMsg {}

    record Loaded(int number, Either<Failure, List<PrototypeComment>> read) implements CommentsPrototypeMsg {}

    record Closed() implements CommentsPrototypeMsg {}
}
