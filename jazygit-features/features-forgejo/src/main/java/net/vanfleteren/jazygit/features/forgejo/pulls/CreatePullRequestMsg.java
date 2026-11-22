package net.vanfleteren.jazygit.features.forgejo.pulls;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.forgejo.ForgejoPullRequest;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.features.forgejo.pulls.CreatePullRequestCmd.CreatePullRequest;
import net.vanfleteren.jazygit.features.forgejo.pulls.CreatePullRequestCmd.PushFirst;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.popup.ListCursor;

/**
 * Creating the pull request on Forgejo of a branch that has none (ADR 178): checking where it is pushed to, pushing
 * it first, reading the title it starts out with, then asking what it merges into and what it says, in one popup.
 */
public sealed interface CreatePullRequestMsg extends Msg {

    @Override
    default Next apply(Model model) {
        return CreatePullRequestUpdate.update(model, this);
    }

    /**
     * Where {@code branch} is pushed to was checked: {@code error} says why its pull request cannot be created there,
     * if it cannot.
     */
    record HeadChecked(BranchName branch, Option<CreateError> error) implements CreatePullRequestMsg {}

    /**
     * The user confirmed pushing the branch before creating its pull request.
     */
    record PushFirstConfirmed() implements CreatePullRequestMsg {}

    /**
     * The branch was pushed by running {@code commands}, so its pull request can be created now.
     */
    record Pushed(PushFirst cmd, List<ExecutedCommand> commands) implements CreatePullRequestMsg {}

    /**
     * The title a new pull request of {@code branch} starts out with was read, for the first of {@code bases}: the
     * subject of its oldest commit not on it, if one was found.
     */
    record TitleRead(BranchName branch, List<BranchName> bases, Option<String> subject)
            implements CreatePullRequestMsg {}

    /**
     * The user moved in the list of bases, to {@code cursor}: the highlighted base, if the filter shows any, is the
     * one the pull request merges into.
     */
    record BaseMoved(ListCursor cursor, Option<BranchName> base) implements CreatePullRequestMsg {}

    /**
     * The title of the new pull request of {@code branch} was read again, for {@code base}, which the user picked
     * instead.
     */
    record BaseTitleRead(BranchName branch, BranchName base, Option<String> subject) implements CreatePullRequestMsg {}

    /**
     * The user changed what the new pull request says.
     */
    record Edited(String title, String body, boolean draft) implements CreatePullRequestMsg {}

    /**
     * The user confirmed what the new pull request says.
     */
    record Confirmed() implements CreatePullRequestMsg {}

    /**
     * Forgejo created {@code created}, as {@code cmd} asked.
     */
    record Created(CreatePullRequest cmd, ForgejoPullRequest created) implements CreatePullRequestMsg {}

    /**
     * Forgejo did not create the pull request {@code cmd} asked for, as pull request {@code number} is open with the
     * same head and base already.
     */
    record AlreadyOpen(CreatePullRequest cmd, long number) implements CreatePullRequestMsg {}
}
