package net.vanfleteren.jazygit.features.branches.branch.base;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.BaseCandidate;
import net.vanfleteren.jazygit.core.git.model.BranchName;
import net.vanfleteren.jazygit.kernel.state.popup.Popup;

/**
 * Asking which branch {@code branch} is built on (ADR 143).
 *
 * @param candidates  the branches it could be built on, the likeliest first
 * @param current     the base it has now, if one is recorded
 * @param createdFrom the branch its reflog says it was made from, if that is one of the candidates
 * @param main        the main branch, its base when none is recorded
 */
public record BasePopup(
        BranchName branch,
        List<BaseCandidate> candidates,
        Option<BranchName> current,
        Option<BranchName> createdFrom,
        Option<BranchName> main)
        implements Popup {

    /**
     * What each row picks, in the order they are listed: each candidate, then recording none.
     */
    public List<Option<BranchName>> choices() {
        return candidates.map(candidate -> Option.of(candidate.name())).append(Option.none());
    }
}
