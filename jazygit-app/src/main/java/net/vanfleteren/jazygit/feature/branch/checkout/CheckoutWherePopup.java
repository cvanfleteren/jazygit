package net.vanfleteren.jazygit.feature.branch.checkout;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.Worktree;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking where {@code request} is checked out, as the worktree shown is a linked one: here, in the main worktree or
 * in a new one (ADR 68).
 *
 * @param here        the worktree shown
 * @param main        the main worktree; none next to a bare repository
 * @param mainChanged whether the main worktree has changes, if that is known
 */
public record CheckoutWherePopup(
        CheckoutRequest request, Worktree here, Option<Worktree> main, Option<Boolean> mainChanged) implements Popup {}
