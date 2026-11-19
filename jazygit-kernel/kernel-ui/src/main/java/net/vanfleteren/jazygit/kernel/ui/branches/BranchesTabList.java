package net.vanfleteren.jazygit.kernel.ui.branches;

import dev.tamboui.text.Line;
import dev.tamboui.toolkit.elements.Panel;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.BranchRef;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.view.Followed;
import net.vanfleteren.jazygit.kernel.state.view.NoLog;

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
     * Why the highlighted row has no log to show, when it has none: a pull request of a stack whose branch is neither
     * checked out nor fetched (ADR 72), or a detached HEAD.
     */
    default Option<NoLog> noLog() {
        return Option.none();
    }

    /**
     * What the files pane lists while the highlighted row of this list has the focus (ADR 141): the files of the
     * working tree, unless the list follows its highlight; none while that is not known yet.
     */
    default Option<Followed> followed(Model model) {
        return Option.of(new Followed.WorkingTree());
    }
}
