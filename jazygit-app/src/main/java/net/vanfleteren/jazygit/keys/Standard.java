package net.vanfleteren.jazygit.keys;

import io.vavr.collection.List;
import java.util.function.UnaryOperator;
import net.vanfleteren.jazygit.feature.branch.BranchKeys;
import net.vanfleteren.jazygit.feature.cherrypick.CherryPickKeys;
import net.vanfleteren.jazygit.feature.commit.CommitKeys;
import net.vanfleteren.jazygit.feature.discard.DiscardKeys;
import net.vanfleteren.jazygit.feature.github.BrowseKeys;
import net.vanfleteren.jazygit.feature.github.PullRequestKeys;
import net.vanfleteren.jazygit.feature.stage.StageKeys;
import net.vanfleteren.jazygit.feature.stash.StashKeys;
import net.vanfleteren.jazygit.feature.worktree.WorktreeKeys;

/**
 * The features that declare keys. The order is the order in which the keys of a panel are listed.
 */
final class Standard {

    static final Keymap KEYMAP = List.<UnaryOperator<Keymap>>of(
                    StageKeys::register,
                    CommitKeys::register,
                    CherryPickKeys::register,
                    DiscardKeys::register,
                    StashKeys::register,
                    BranchKeys::register,
                    PullRequestKeys::register,
                    BrowseKeys::register,
                    WorktreeKeys::register,
                    GlobalKeys::register)
            .foldLeft(Keymap.empty(), (keymap, feature) -> feature.apply(keymap));

    private Standard() {}
}
