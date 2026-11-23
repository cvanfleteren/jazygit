package net.vanfleteren.jazygit.features.changes.commit;

import net.vanfleteren.jazygit.features.changes.commit.amend.AmendAction;
import net.vanfleteren.jazygit.features.changes.commit.create.CreateCommitAction;
import net.vanfleteren.jazygit.features.changes.commit.drop.DropAction;
import net.vanfleteren.jazygit.features.changes.commit.fixup.FixupAction;
import net.vanfleteren.jazygit.features.changes.commit.reset.ResetAction;
import net.vanfleteren.jazygit.features.changes.commit.revert.RevertAction;
import net.vanfleteren.jazygit.features.changes.commit.reword.RewordAction;
import net.vanfleteren.jazygit.features.changes.commit.search.CommitSearchAction;
import net.vanfleteren.jazygit.features.changes.commit.search.content.CommitSearchShown;
import net.vanfleteren.jazygit.features.changes.commit.squash.SquashAction;
import net.vanfleteren.jazygit.features.changes.panes.commits.CommitsPane;
import net.vanfleteren.jazygit.features.changes.panes.commits.CommitsTabsAction;
import net.vanfleteren.jazygit.kernel.ui.Feature;

/**
 * The commit operations: committing, amending (the last or any commit), rewording, squashing, fixing up, dropping,
 * reverting, resetting, searching them, and listing only those not on the base. Adding one is adding its subpackage, and listing its action here.
 */
public final class CommitFeature {

    /**
     * The operations, in the order their keys are listed in a panel. The search shows its line and what it found in
     * the commits pane, through its keys (ADR 172); the last keys switch between the tabs of that pane (ADR 185).
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
                    CommitsTabsAction.ACTION)
            .filling(CommitsPane.LINE, CommitSearchShown::line)
            .filling(CommitsPane.LISTING, CommitSearchShown::listing);

    private CommitFeature() {}
}
