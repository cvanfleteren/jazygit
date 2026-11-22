package net.vanfleteren.jazygit.features.forgejo.pulls;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.kernel.state.popup.Popup;

/**
 * Asking to push {@code branch} to {@code target} before creating its pull request on Forgejo (ADR 25, 178).
 *
 * @param unpushed the number of its commits {@code target} does not have; none when it tracks no remote branch
 */
public record PushFirstPopup(BranchName branch, RemoteBranch target, Option<Integer> unpushed) implements Popup {}
