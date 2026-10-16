package net.vanfleteren.jazygit.feature.branch;

import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

public sealed interface UpstreamMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return UpstreamUpdate.update(model, this);
    }

    /**
     * The user asked for the options on the upstream of the checked out branch.
     */
    record Requested() implements UpstreamMsg {}

    record Cancelled() implements UpstreamMsg {}

    /**
     * The user chose to reset the checked out branch onto its upstream; how is still to be chosen.
     */
    record ResetChosen() implements UpstreamMsg {}
}
