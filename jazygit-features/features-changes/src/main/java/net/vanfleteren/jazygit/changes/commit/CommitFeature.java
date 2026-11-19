package net.vanfleteren.jazygit.changes.commit;

import net.vanfleteren.jazygit.changes.commit.amend.AmendAction;
import net.vanfleteren.jazygit.changes.commit.create.CreateCommitAction;
import net.vanfleteren.jazygit.changes.commit.drop.DropAction;
import net.vanfleteren.jazygit.changes.commit.fixup.FixupAction;
import net.vanfleteren.jazygit.changes.commit.reset.ResetAction;
import net.vanfleteren.jazygit.changes.commit.revert.RevertAction;
import net.vanfleteren.jazygit.changes.commit.reword.RewordAction;
import net.vanfleteren.jazygit.changes.commit.search.CommitSearchAction;
import net.vanfleteren.jazygit.changes.commit.squash.SquashAction;
import net.vanfleteren.jazygit.ui.Feature;

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
            CommitSearchAction.ACTION);

    private CommitFeature() {}
}
