package net.vanfleteren.jazygit.features.forgejo.pulls;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.forgejo.ForgejoPullRequest;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.features.forgejo.pulls.CreatePullRequestCmd.CreatePullRequest;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update.Next;

/**
 * Creating the pull request on Forgejo of a branch that has none (ADR 178): checking where it is pushed to, then
 * asking what it merges into and what it says, in one popup. Pushing it first and the title that follows the base are
 * {@code NewPullRequestMsg}'s.
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
