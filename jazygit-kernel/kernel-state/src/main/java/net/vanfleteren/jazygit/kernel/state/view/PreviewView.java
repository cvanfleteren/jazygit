package net.vanfleteren.jazygit.kernel.state.view;

import io.vavr.collection.List;
import io.vavr.control.Option;
import lombok.With;
import net.vanfleteren.jazygit.core.git.model.*;
import net.vanfleteren.jazygit.core.repo.*;

/**
 * What is read for the item highlighted in each pane. They are separate, not one preview: each pane keeps its own while
 * the focus is in another pane, and the content panel shows the one of the pane that has the focus.
 *
 * @param branchLog    the log of the branch, local or remote, highlighted in the branches pane, once one is
 *                     highlighted
 * @param branchAhead  the commits of the branch whose log is shown that are not on its base, keyed by the branch; none
 *                     in it when the branch has no base
 * @param commitDetail what the commit highlighted in the commits pane changed (its files, a summary and its diff),
 *                     once one is highlighted
 * @param fileDiff     the diff of the node highlighted in the files pane, keyed by the changed files under it, once
 *                     one is highlighted
 * @param stashDetail  what the stash highlighted in the stash pane stashed (its files, a summary and its diff), keyed
 *                     by its sha, which stays as stashes are added and dropped, once one is highlighted
 */
@With
public record PreviewView(
        Option<Preview<BranchRef, List<Commit>>> branchLog,
        Option<Preview<BranchRef, Option<Ahead>>> branchAhead,
        Option<Preview<Sha, DiffText>> commitDetail,
        Option<Preview<List<FileEntry>, Diffs>> fileDiff,
        Option<Preview<Sha, DiffText>> stashDetail) {

    public static final PreviewView INITIAL =
            new PreviewView(Option.none(), Option.none(), Option.none(), Option.none(), Option.none());
}
