package net.vanfleteren.jazygit.features.changes.commit.amend;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.changes.commit.PushedCheckCmd;
import net.vanfleteren.jazygit.features.changes.commit.PushedWarning;
import net.vanfleteren.jazygit.kernel.state.Msg;

/**
 * The commit that is amended.
 */
public sealed interface AmendTarget extends PushedCheckCmd.Then {

    @Override
    default Msg checked(Option<PushedWarning> warning) {
        return new AmendMsg.Checked(this, warning);
    }

    /**
     * HEAD, which is amended in place: also while something is in progress, and also when it is a merge.
     */
    record LastCommit() implements AmendTarget {}

    /**
     * A commit below HEAD, which is amended by rebasing the commits after it; it is not a merge.
     */
    record Older(Sha sha) implements AmendTarget {}
}
