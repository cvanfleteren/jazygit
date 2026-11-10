package net.vanfleteren.jazygit.feature.github.verdict;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.feature.FeatureAction;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.ui.PopupSlot;

/**
 * Approving a pull request, or asking for changes (ADR 99): {@code a} in the diff of its files, and the popup that
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
