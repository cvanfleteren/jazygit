package net.vanfleteren.jazygit.features.forgejo.pulls;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.ui.PopupSlot;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;

/**
 * The popups of creating a pull request on Forgejo (ADR 178): what it merges into and what it says. Whether to push
 * the branch first is {@code NewPullRequestDialogs}'.
 */
public final class CreatePullRequestDialogs {

    private CreatePullRequestDialogs() {}

    static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(PopupSlot.returningTo(Scopes.BRANCHES.pane(), new ForgejoNewPullRequestDialog(dispatch)));
    }
}
