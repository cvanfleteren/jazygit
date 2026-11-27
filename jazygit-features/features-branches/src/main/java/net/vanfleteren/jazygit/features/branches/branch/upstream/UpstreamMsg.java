package net.vanfleteren.jazygit.features.branches.branch.upstream;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;

public sealed interface UpstreamMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return UpstreamUpdate.update(model, this);
    }

    /**
     * The user asked for the options on the upstream, with {@code branch} highlighted, if any.
     */
    record Requested(Option<BranchName> branch) implements UpstreamMsg {}

    /**
     * The user chose to reset the checked out branch onto its upstream; how is still to be chosen.
     */
    record ResetChosen() implements UpstreamMsg {}

    /**
     * The user chose to set the upstream of the highlighted branch; which one is still to be entered.
     */
    record SetChosen() implements UpstreamMsg {}

    /**
     * The user confirmed the upstream entered.
     */
    record SetConfirmed() implements UpstreamMsg {}

    /**
     * The user chose to unset the upstream of the highlighted branch.
     */
    record UnsetChosen() implements UpstreamMsg {}

    /**
     * The user chose to see the commits the highlighted branch and its upstream do not share.
     */
    record UpstreamDivergenceChosen() implements UpstreamMsg {}

    /**
     * The user chose to see the commits the highlighted branch and the main branch do not share.
     */
    record BaseDivergenceChosen() implements UpstreamMsg {}

    /**
     * The user closed the divergence, to see the branches again.
     */
    record DivergenceClosed() implements UpstreamMsg {}
}
