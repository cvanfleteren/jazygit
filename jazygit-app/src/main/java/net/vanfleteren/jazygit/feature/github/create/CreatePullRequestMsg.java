package net.vanfleteren.jazygit.feature.github.create;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.NewPullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.ListCursor;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

/**
 * Creating the pull request of a branch that has none: reading the branch it came from and the title it starts out
 * with, then asking what it merges into and what it says, in one popup.
 */
public sealed interface CreatePullRequestMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return CreatePullRequestUpdate.update(model, this);
    }

    /**
     * The branch {@code branch} was made from was read, if it could be told: the base that is offered first.
     */
    record ParentRead(Branch.Name branch, Option<Branch.Name> parent) implements CreatePullRequestMsg {}

    /**
     * The user moved in the list of bases, to {@code cursor}: the highlighted base, if the filter shows any, is the
     * one the pull request merges into.
     */
    record BaseMoved(ListCursor cursor, Option<Branch.Name> base) implements CreatePullRequestMsg {}

    /**
     * The title a new pull request of {@code branch} starts out with was read, for the first of {@code bases}: the
     * subject of its oldest commit not on it, if one was found.
     *
     * @param head  the name the branch has on its remote
     * @param bases the branches it can merge into, the one it was read for first
     */
    record TitleRead(Branch.Name branch, Branch.Name head, List<Branch.Name> bases, Option<String> subject)
            implements CreatePullRequestMsg {}

    /**
     * The title of the new pull request of {@code branch} was read again, for {@code base}, which the user picked
     * instead.
     */
    record BaseTitleRead(Branch.Name branch, Branch.Name base, Option<String> subject)
            implements CreatePullRequestMsg {}

    /**
     * The user changed what the new pull request says.
     */
    record PullRequestEdited(NewPullRequest pullRequest) implements CreatePullRequestMsg {}

    /**
     * The user confirmed what the new pull request says.
     */
    record Confirmed() implements CreatePullRequestMsg {}

    record Cancelled() implements CreatePullRequestMsg {}

    /**
     * The pull request was created by running {@code commands}.
     *
     * @param pullRequest the new pull request, as far as it is known before it is read from GitHub
     */
    record Created(
            CreatePullRequestCmd.CreatePullRequest cmd, Option<PullRequest> pullRequest, List<ExecutedCommand> commands)
            implements CreatePullRequestMsg {}
}
