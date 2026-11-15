package net.vanfleteren.jazygit.github.verdict;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.ui.FeatureAction;
import net.vanfleteren.jazygit.ui.PopupSlot;
import net.vanfleteren.jazygit.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.ui.keys.Scopes;

/**
 * Approving a pull request, or asking for changes (ADR 100): {@code a} in the diff of its files, and the popup that
 * writes the review, which the pull request dialog opens too.
 */
public final class VerdictAction {

    public static final FeatureAction ACTION = new FeatureAction(
            keys -> keys.add(
                    Scopes.PULL_REQUEST_DIFF,
                    Binding.always("a", "help.pullRequestDiff.review", VerdictMsg.DiffAsked::new)),
            dispatch -> List.of(PopupSlot.returningToFocus(new VerdictDialog(dispatch))));

    private VerdictAction() {}
}
