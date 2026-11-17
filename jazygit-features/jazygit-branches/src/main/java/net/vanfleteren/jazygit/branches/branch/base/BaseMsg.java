package net.vanfleteren.jazygit.branches.branch.base;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.BaseCandidates;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

/**
 * Setting the branch a branch is built on (ADR 143).
 */
public sealed interface BaseMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return BaseUpdate.update(model, this);
    }

    /**
     * The user asked to set the base of {@code branch}.
     */
    record Requested(BranchName branch) implements BaseMsg {}

    /**
     * What {@code branch} could be built on, as git read it.
     */
    record CandidatesRead(BranchName branch, BaseCandidates candidates) implements BaseMsg {}

    /**
     * Reading what {@code branch} could be built on failed.
     */
    record CandidatesFailed(BranchName branch, Failure failure) implements BaseMsg {}

    /**
     * The user picked {@code base} as the base of the branch of the popup; none to record none, so that the main
     * branch is its base.
     */
    record Chosen(Option<BranchName> base) implements BaseMsg {}
}
