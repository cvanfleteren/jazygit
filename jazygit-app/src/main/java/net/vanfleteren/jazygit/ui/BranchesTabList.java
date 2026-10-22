package net.vanfleteren.jazygit.ui;

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
}
