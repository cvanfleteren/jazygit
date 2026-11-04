package net.vanfleteren.jazygit.feature.branch.stack;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.StackLayer;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

public sealed interface StackMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return StackUpdate.update(model, this);
    }

    /**
     * The user opened or closed the stack numbered {@code stack} in the branches pane (ADR 72).
     */
    record Toggled(int stack) implements StackMsg {}

    /**
     * The user asked what can be done with the stack of the highlighted branch, or of the highlighted stack
     * {@code stack} (ADR 73).
     */
    record MenuRequested(Option<BranchName> branch, Option<Integer> stack) implements StackMsg {}

    /**
     * {@code cmd} changed the stacks on GitHub, running {@code commands} (ADR 73).
     */
    record Changed(StackCmd cmd, List<ExecutedCommand> commands) implements StackMsg {}

    /**
     * Time to read the pull requests again, while they may not show a change made to the stacks yet.
     */
    record Tick() implements StackMsg {}

    /**
     * The user chose to make a stack from the branch of the menu.
     */
    record MakeChosen() implements StackMsg {}

    /**
     * The user chose to put branches on the top of the stack of the menu.
     */
    record AddChosen() implements StackMsg {}

    /**
     * The user chose to stack the pull request of the branch of the menu on another one.
     */
    record StackOnChosen() implements StackMsg {}

    /**
     * The user chose {@code target} to stack the pull request of the branch on.
     */
    record TargetChosen(StackPopups.Target target) implements StackMsg {}

    /**
     * The user chose to rebase the stack of the menu (ADR 74).
     */
    record RebaseChosen() implements StackMsg {}

    /**
     * The user chose to push the stack of the menu (ADR 74).
     */
    record PushChosen() implements StackMsg {}

    /**
     * The own commits of the branches of {@code rebase} were read, to ask about rebasing them.
     */
    record StackRead(StackRebase rebase, List<StackLayer.Commits> commits) implements StackMsg {}

    /**
     * The own commits of the branches of stack {@code stack} could not be read.
     */
    record StackReadFailed(int stack, Failure failure) implements StackMsg {}

    /**
     * {@code cmd} rebased its stack, running {@code commands}.
     */
    record Rebased(StackRebaseCmd.RebaseStack cmd, List<ExecutedCommand> commands) implements StackMsg {}

    /**
     * The user chose to take the stack of the menu apart.
     */
    record UnstackChosen() implements StackMsg {}

    /**
     * The user made the highlighted branch of the stack being made its bottom.
     */
    record BottomChosen(int row) implements StackMsg {}

    /**
     * The user made the highlighted branch of the stack being made its top.
     */
    record TopChosen(int row) implements StackMsg {}

    /**
     * The user asked to make the stack of the branches chosen.
     */
    record PickConfirmed() implements StackMsg {}
}
