package net.vanfleteren.jazygit.feature.branch.upstream;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking what to do with the upstream of a branch.
 *
 * @param branch     the highlighted branch, whose divergence can be viewed; none when the branches are not
 *                   loaded yet
 * @param base       the main branch it would be compared with, if one is known
 * @param checkedOut the checked out branch, which can be reset onto its upstream; none when no branch is checked
 *                   out, or the branches are not loaded yet
 */
public record UpstreamPopup(Option<Branch> branch, Option<BranchRef> base, Option<Branch> checkedOut)
        implements Popup {}
