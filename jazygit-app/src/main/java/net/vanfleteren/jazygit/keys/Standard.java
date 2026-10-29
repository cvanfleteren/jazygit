package net.vanfleteren.jazygit.keys;

import io.vavr.collection.List;
import java.util.function.UnaryOperator;
import net.vanfleteren.jazygit.feature.branch.BranchFeature;
import net.vanfleteren.jazygit.feature.cherrypick.CherryPickKeys;
import net.vanfleteren.jazygit.feature.commit.CommitFeature;
import net.vanfleteren.jazygit.feature.diff.CommitFilesAction;
import net.vanfleteren.jazygit.feature.discard.DiscardKeys;
import net.vanfleteren.jazygit.feature.edit.EditKeys;
import net.vanfleteren.jazygit.feature.github.GitHubFeature;
import net.vanfleteren.jazygit.feature.stage.StageKeys;
import net.vanfleteren.jazygit.feature.stash.StashKeys;
import net.vanfleteren.jazygit.feature.worktree.WorktreeFeature;

/**
 * The features that declare keys. The order is the order in which the keys of a panel are listed.
 */
final class Standard {

    static final Keymap KEYMAP = List.<UnaryOperator<Keymap>>of(
                    StageKeys::register,
                    CommitFeature::register,
                    CherryPickKeys::register,
                    DiscardKeys::register,
                    EditKeys::register,
                    StashKeys::register,
                    BranchFeature::register,
                    GitHubFeature::register,
                    WorktreeFeature::register,
                    CommitFilesAction.ACTION.keys(),
                    GlobalKeys::register)
            .foldLeft(Keymap.empty(), (keymap, feature) -> feature.apply(keymap));

    private Standard() {}
}
