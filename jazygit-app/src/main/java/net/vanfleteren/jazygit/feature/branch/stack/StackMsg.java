package net.vanfleteren.jazygit.feature.branch.stack;

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
}
