package net.vanfleteren.jazygit.feature.branch.stack;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.BranchName;
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
