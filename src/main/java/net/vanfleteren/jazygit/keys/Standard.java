package net.vanfleteren.jazygit.keys;

import net.vanfleteren.jazygit.feature.branch.BranchKeys;
import net.vanfleteren.jazygit.feature.commit.CommitKeys;
import net.vanfleteren.jazygit.feature.discard.DiscardKeys;
import net.vanfleteren.jazygit.feature.stage.StageKeys;
import net.vanfleteren.jazygit.feature.stash.StashKeys;

/**
 * The features that declare keys. The order is the order in which the keys of a panel are listed.
 */
final class Standard {

    static final Keymap KEYMAP;

    static {
        Keymap.Builder builder = Keymap.builder();
        StageKeys.register(builder);
        CommitKeys.register(builder);
        DiscardKeys.register(builder);
        StashKeys.register(builder);
        BranchKeys.register(builder);
        GlobalKeys.register(builder);
        KEYMAP = builder.build();
    }

    private Standard() {
    }
}
