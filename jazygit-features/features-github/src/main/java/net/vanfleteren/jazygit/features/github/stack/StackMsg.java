package net.vanfleteren.jazygit.features.github.stack;

import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.core.gh.model.StackId;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.StackLayer;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;

public sealed interface StackMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return StackUpdate.update(model, this);
    }

    /**
     * The stacks shown in the branches pane, and the menu of one (ADR 72, 73, 99).
     */
    sealed interface Showing extends StackMsg {}

    /**
     * Making a stack, adding to one, stacking a pull request on another, and taking a stack apart (ADR 73, 148).
     */
    sealed interface Editing extends StackMsg {}

    /**
     * Rebasing a stack and pushing it (ADR 74).
     */
    sealed interface Rebasing extends StackMsg {}

    /**
     * Checking out a stack, or updating its branches (ADR 99).
     */
    sealed interface CheckingOut extends StackMsg {}

    /**
     * The user opened or closed the stack numbered {@code stack} in the branches pane (ADR 72).
     */
    record Toggled(StackId stack) implements Showing {}

    /**
     * The user switched the branches pane between showing the stacks and listing their branches as any other (ADR 72).
     */
    record ViewToggled() implements Showing {}

    /**
     * The user asked what can be done with the stack of the highlighted branch, or of the highlighted stack
     * {@code stack} (ADR 73).
     */
    record MenuRequested(Option<BranchName> branch, Option<StackId> stack) implements Showing {}

    /**
     * The user asked what can be done with the stack of {@code pullRequest}, highlighted on the pull requests screen
     * (ADR 99).
     */
    record PullRequestMenuRequested(PullRequestSummary pullRequest) implements Showing {}

    /**
     * The user chose to check out the stack of the menu, or to update its branches (ADR 99).
     */
    record CheckoutChosen() implements CheckingOut {}

    /**
     * {@code cmd} fetched the branches of its stack, running {@code commands}; {@code checkout} says what checking it
     * out does to each.
     */
    record StackFetched(StackCheckoutCmd.Fetch cmd, List<ExecutedCommand> commands, StackCheckout checkout)
            implements CheckingOut {}

    /**
     * The user confirmed checking out the stack asked about, landing where {@code landing} says.
     */
    record CheckoutConfirmed(StackCheckout.Landing landing) implements CheckingOut {}

    /**
     * {@code cmd} made and fast-forwarded the branches of its stack, running {@code commands}.
     */
    record CaughtUp(StackCheckoutCmd.CatchUp cmd, List<ExecutedCommand> commands) implements CheckingOut {}

    /**
     * {@code cmd} changed the stacks on GitHub, running {@code commands} (ADR 73).
     */
    record Changed(StackCmd cmd, List<ExecutedCommand> commands) implements StackMsg {}

    /**
     * {@code cmd} failed, maybe after changing GitHub already, as the bases of the pull requests before GitHub refused
     * the stack (ADR 73).
     */
    record ChangeFailed(StackCmd cmd, Failure failure) implements StackMsg {}

    /**
     * Time to read the pull requests again, while they may not show a change made to the stacks yet.
     */
    record Tick() implements StackMsg {}

    /**
     * The user chose to make a stack from the branch of the menu.
     */
    record MakeChosen() implements Editing {}

    /**
     * The user chose to put branches on the top of the stack of the menu.
     */
    record AddChosen() implements Editing {}

    /**
     * Which of {@code eligible} are built on {@code top}, the top of stack {@code stack}, by their history, was read
     * (ADR 148): for {@code top} and each branch found, the local branches that have its tip; nothing when it could
     * not be read.
     */
    record BuiltOnRead(
            StackId stack, BranchName top, Set<BranchName> eligible, Map<BranchName, List<BranchName>> containing)
            implements Editing {}

    /**
     * The user chose to stack the branch of the menu on another one: its pull request, or one created for it.
     */
    record StackOnChosen() implements Editing {}

    /**
     * The user chose {@code target} to stack the pull request of the branch on.
     */
    record TargetChosen(StackPopups.Target target) implements Editing {}

    /**
     * The user chose to link the branches asked about, and to rebase their stack once GitHub lists it (ADR 74).
     */
    record LinkAndRebaseChosen() implements Rebasing {}

    /**
     * The user chose to rebase the stack of the menu (ADR 74).
     */
    record RebaseChosen() implements Rebasing {}

    /**
     * The user chose to push the stack of the menu (ADR 74).
     */
    record PushChosen() implements Rebasing {}

    /**
     * The own commits of the branches of {@code rebase} were read, to ask about rebasing them.
     */
    record StackRead(StackRebase rebase, List<StackLayer.Commits> commits) implements Rebasing {}

    /**
     * The own commits of the branches of stack {@code stack} could not be read.
     */
    record StackReadFailed(StackId stack, Failure failure) implements Rebasing {}

    /**
     * {@code branch} of stack {@code stack} lacks commits that {@code remote}, its branch on the remote, has: it is
     * not rebased, as it would be pushed over them (ADR 74).
     */
    record StackBehindRemote(StackId stack, BranchName branch, RemoteBranch remote) implements Rebasing {}

    /**
     * The user chose to rebase the stack asked about without pushing it (ADR 74).
     */
    record RebaseLocallyChosen() implements Rebasing {}

    /**
     * The user chose to take the stack of the menu apart.
     */
    record UnstackChosen() implements Editing {}

    /**
     * The user made the highlighted branch of the stack being made its bottom.
     */
    record BottomChosen(int row) implements Editing {}

    /**
     * The user made the highlighted branch of the stack being made its top.
     */
    record TopChosen(int row) implements Editing {}

    /**
     * The user asked to make the stack of the branches chosen.
     */
    record PickConfirmed() implements Editing {}
}
