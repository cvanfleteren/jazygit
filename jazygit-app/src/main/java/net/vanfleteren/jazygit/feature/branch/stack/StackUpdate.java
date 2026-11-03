package net.vanfleteren.jazygit.feature.branch.stack;

import net.vanfleteren.jazygit.feature.branch.stack.StackMsg.Toggled;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Opening and closing a stack in the branches pane (ADR 72). Whether it is open is view state, as the cursors are,
 * and is not kept.
 */
public final class StackUpdate {

    private StackUpdate() {}

    public static Next update(Model model, StackMsg msg) {
        return switch (msg) {
            case Toggled(int stack) ->
                Next.of(model.mapRepoView(repoView -> repoView.withClosedStacks(
                        repoView.closedStacks().contains(stack)
                                ? repoView.closedStacks().remove(stack)
                                : repoView.closedStacks().add(stack))));
        };
    }
}
