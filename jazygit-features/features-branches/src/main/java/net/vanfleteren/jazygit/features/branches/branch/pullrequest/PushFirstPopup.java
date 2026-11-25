package net.vanfleteren.jazygit.features.branches.branch.pullrequest;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.kernel.state.popup.Popup;

/**
 * Asking to push {@code branch} to {@code target} before creating its pull request (ADR 25), on any forge.
 *
 * @param unpushed the number of its commits {@code target} does not have; none when it tracks no remote branch
 * @param then     what the forge does once it is pushed
 */
public record PushFirstPopup(BranchName branch, RemoteBranch target, Option<Integer> unpushed, AfterPush then)
        implements Popup {}
