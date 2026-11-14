package net.vanfleteren.jazygit.feature.commit;

import net.vanfleteren.jazygit.feature.Feature;
import net.vanfleteren.jazygit.feature.ahead.AheadAction;
import net.vanfleteren.jazygit.feature.commit.amend.AmendAction;
import net.vanfleteren.jazygit.feature.commit.create.CreateCommitAction;
import net.vanfleteren.jazygit.feature.commit.drop.DropAction;
import net.vanfleteren.jazygit.feature.commit.fixup.FixupAction;
import net.vanfleteren.jazygit.feature.commit.reset.ResetAction;
import net.vanfleteren.jazygit.feature.commit.revert.RevertAction;
import net.vanfleteren.jazygit.feature.commit.reword.RewordAction;
import net.vanfleteren.jazygit.feature.commit.search.CommitSearchAction;
import net.vanfleteren.jazygit.feature.commit.squash.SquashAction;

/**
 * The commit operations: committing, amending (the last or any commit), rewording, squashing, fixing up, dropping,
 * reverting, resetting, searching them, and listing only those not on the base. Adding one is adding its subpackage, and listing its action here.
 */
public final class CommitFeature {

    /**
     * The operations, in the order their keys are listed in a panel.
     */
    public static final Feature FEATURE = Feature.of(
            CreateCommitAction.ACTION,
            AmendAction.ACTION,
            RewordAction.ACTION,
            SquashAction.ACTION,
            FixupAction.ACTION,
            DropAction.ACTION,
            RevertAction.ACTION,
            ResetAction.ACTION,
            CommitSearchAction.ACTION,
            AheadAction.COMMITS);

    private CommitFeature() {}
}
