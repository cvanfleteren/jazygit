package net.vanfleteren.jazygit.ui.branches;

import dev.tamboui.text.Line;
import dev.tamboui.toolkit.elements.Panel;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.state.Model;

/**
 * One of the lists the branches pane shows a tab of: the local branches, the remote ones or the worktrees.
 */
interface BranchesTabList {

    /**
     * @param title the title of the pane, which names all the tabs
     */
    Panel render(Model model, boolean focused, boolean active, Line title);

    /**
     * The branch whose log the highlighted row shows, if any.
     */
    Option<BranchRef> selectedRef();

    /**
     * The pull request the highlighted row shows when it has no log to show: one of a stack whose branch is neither
     * checked out nor fetched (ADR 72).
     */
    default Option<Integer> unfetched() {
        return Option.none();
    }
}
