package net.vanfleteren.jazygit.features.branches.branch.pullrequest;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.features.branches.branch.pullrequest.NewPullRequestCmd.PushFirst;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.popup.ListCursor;

/**
 * Creating a pull request, as far as it is the same on every forge: pushing the branch first (ADR 25), and the title
 * that follows the base picked (ADR 18).
 */
public sealed interface NewPullRequestMsg extends Msg {

    @Override
    default Next apply(Model model) {
        return NewPullRequestUpdate.update(model, this);
    }

    /**
     * The user confirmed pushing the branch before creating its pull request.
     */
    record PushFirstConfirmed() implements NewPullRequestMsg {}

    /**
     * The branch was pushed by running {@code commands}, so its pull request can be created now.
     */
    record Pushed(PushFirst cmd, List<ExecutedCommand> commands) implements NewPullRequestMsg {}

    /**
     * The title a new pull request of {@code branch} starts out with was read, for the base offered first: the subject
     * of its oldest commit not on it, if one was found. {@code opening} opens the popup with it.
     */
    record TitleRead(BranchName branch, FormOpening opening, Option<String> subject) implements NewPullRequestMsg {}

    /**
     * The user moved in the list of bases, to {@code cursor}: the highlighted base, if the filter shows any, is the
     * one the pull request merges into.
     */
    record BaseMoved(ListCursor cursor, Option<BranchName> base) implements NewPullRequestMsg {}

    /**
     * The title of the new pull request of {@code branch} was read again, for {@code base}, which the user picked
     * instead.
     */
    record BaseTitleRead(BranchName branch, BranchName base, Option<String> subject) implements NewPullRequestMsg {}
}
