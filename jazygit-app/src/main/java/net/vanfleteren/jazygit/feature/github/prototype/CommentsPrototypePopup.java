package net.vanfleteren.jazygit.feature.github.prototype;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.github.show.PullRequestPopup;
import net.vanfleteren.jazygit.gh.model.PrototypeComment;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Popup;

/**
 * PROTOTYPE: the comments of the pull request {@code shown}; nothing while they are being read.
 */
public record CommentsPrototypePopup(PullRequestPopup shown, Option<Either<Failure, List<PrototypeComment>>> comments)
        implements Popup {

    public int number() {
        return shown.pullRequest().number();
    }
}
