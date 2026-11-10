package net.vanfleteren.jazygit.feature.branch.stack;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.stack.StackChoice.Plan;
import net.vanfleteren.jazygit.feature.branch.stack.StackChoice.Refusal;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.State;
import net.vanfleteren.jazygit.gh.model.PullRequestStack;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.state.ConfirmablePopup;
import net.vanfleteren.jazygit.state.Popup;

/**
 * The popups of making and unmaking a stack (ADR 73).
 */
public final class StackPopups {

    private StackPopups() {}

    /**
     * What can be done with the stack of {@code branch}, or with {@code stack}.
     *
     * @param branch      the highlighted branch, none on the header of a stack
     * @param pullRequest the open pull request of the branch, if it has one
     * @param stack       the stack of the branch on GitHub, or the highlighted one
     * @param top         the local branch of the top of the stack, if it is checked out
     * @param root        whether the branch is a root, which is never stacked
     * @param targets     the branches the pull request of the branch can be stacked on
     * @param on          the number of the pull request it was opened on: that of the branch, or the one highlighted
     *                    on the pull requests screen; none on the header of a stack
     * @param checkout    what the branches of the stack are, which says whether it is checked out or updated (ADR 99)
     */
    public record Menu(
            Option<Branch> branch,
            Option<PullRequest> pullRequest,
            Option<PullRequestStack> stack,
            Option<Branch> top,
            boolean root,
            List<Target> targets,
            Option<Integer> on,
            StackCheckout.Status checkout)
            implements Popup {

        /**
         * Whether the stack can be checked out, or its branches updated: some of them is not here, or may not be where
         * GitHub has it (ADR 99).
         */
        public boolean canCheckOut() {
            return stack.isDefined()
                    && (checkout == StackCheckout.Status.CHECK_OUT || checkout == StackCheckout.Status.UPDATE);
        }

        /**
         * Whether a stack can be made from the branch: one in no stack that is no root.
         */
        public boolean canMake() {
            return branch.isDefined() && stack.isEmpty() && !root;
        }

        /**
         * Whether branches can be put on the top of the stack: when the branch of its top is checked out.
         */
        public boolean canAdd() {
            return stack.isDefined() && top.isDefined();
        }

        /**
         * Whether the pull request of the branch can be stacked on another one.
         */
        public boolean canStackOn() {
            return canMake() && pullRequest.isDefined() && !targets.isEmpty();
        }

        /**
         * Whether the stack can be taken apart, rebased and pushed: any stack on GitHub (ADR 73, ADR 74).
         */
        public boolean canUnstack() {
            return stack.isDefined();
        }

        /**
         * Whether anything of the menu applies.
         */
        public boolean anyApplies() {
            return canMake() || canAdd() || canStackOn() || canUnstack();
        }
    }

    /**
     * A branch whose pull request another can be stacked on: one in no stack, or on the top of one.
     */
    public record Target(Branch branch, PullRequest pullRequest) {}

    /**
     * Choosing the bottom and the top of a stack to make, or the top of what to put on stack {@code addTo}.
     *
     * @param choice  the branches and what is chosen of them
     * @param refused why the last bottom or top chosen was refused, as a key of the messages
     * @param addTo   the stack the branches are put on, whose top is the fixed bottom; none to make a stack
     */
    public record Pick(StackChoice choice, Option<String> refused, Option<Integer> addTo) implements Popup {

        public Pick withChoice(StackChoice next) {
            return new Pick(next, Option.none(), addTo);
        }

        public Pick withRefused(String key) {
            return new Pick(choice, Option.of(key), addTo);
        }

        /**
         * What making the stack takes; or why what is chosen makes none.
         */
        public Either<Refusal, Plan> plan() {
            return addTo.isDefined() ? choice.plan(1, 1) : choice.plan(2, 0);
        }
    }

    /**
     * Choosing the pull request to stack the one of {@code branch} on.
     */
    public record StackOn(Branch branch, PullRequest pullRequest, List<Target> targets) implements Popup {}

    /**
     * Asking before changing the bases of pull requests on GitHub to make {@code cmd}'s stack, or before stacking
     * branches that do not have all the commits of the one below, which GitHub does not merge until they are rebased.
     *
     * @param diverged the branches of the stack that do not have all the commits of the one below them
     */
    public record Confirm(StackCmd.MakeStack cmd, List<Diverged> diverged) implements ConfirmablePopup {

        @Override
        public Option<StackCmd.MakeStack> confirm() {
            return Option.of(cmd);
        }
    }

    /**
     * A branch to stack that does not have the last {@code behind} commits of {@code below}, the one under it.
     */
    public record Diverged(BranchName branch, BranchName below, int behind) {}

    /**
     * Asking before taking {@code stack} apart on GitHub.
     */
    public record Unstack(PullRequestStack stack) implements ConfirmablePopup {

        @Override
        public Option<StackCmd.Unstack> confirm() {
            return Option.of(new StackCmd.Unstack(stack.number()));
        }

        /** How many of its pull requests are open and leave it, which stay open, unlinked. */
        public int open() {
            return stack.entries().count(entry -> entry.state() == State.OPEN && !entry.staysWhenUnstacked());
        }

        /** How many of its pull requests are merged or in a merge queue, which stay in the stack on GitHub. */
        public int staying() {
            return stack.entries().count(PullRequestStack.Entry::staysWhenUnstacked);
        }
    }

    /**
     * Asking before checking out a stack: what happens to the branch of each of its open pull requests, and which of
     * them is checked out (ADR 99).
     */
    public record Checkout(StackCheckout checkout) implements Popup {}

    /**
     * Asking before rebasing a stack and pushing it as {@code cmd} does: which branches, onto what, and the merges it
     * drops (ADR 74). Confirming it rebases and pushes; it can also rebase only.
     */
    public record RebaseConfirm(StackRebaseCmd.RebaseStack cmd) implements ConfirmablePopup {

        @Override
        public Option<StackRebaseCmd.RebaseStack> confirm() {
            return Option.of(cmd);
        }
    }
}
