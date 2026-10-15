package net.vanfleteren.jazygit.keys;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.feature.branch.BranchKeys;
import net.vanfleteren.jazygit.feature.commit.CommitKeys;
import net.vanfleteren.jazygit.feature.discard.DiscardKeys;
import net.vanfleteren.jazygit.feature.github.BrowseKeys;
import net.vanfleteren.jazygit.feature.github.PullRequestKeys;
import net.vanfleteren.jazygit.feature.stage.StageKeys;
import net.vanfleteren.jazygit.feature.stash.StashKeys;

import java.util.function.UnaryOperator;

/**
 * The features that declare keys. The order is the order in which the keys of a panel are listed.
 */
final class Standard {

    static final Keymap KEYMAP = List.<UnaryOperator<Keymap>>of(
                    StageKeys::register,
                    CommitKeys::register,
                    DiscardKeys::register,
                    StashKeys::register,
                    BranchKeys::register,
                    PullRequestKeys::register,
                    BrowseKeys::register,
                    GlobalKeys::register)
            .foldLeft(Keymap.empty(), (keymap, feature) -> feature.apply(keymap));

    private Standard() {
    }
}
