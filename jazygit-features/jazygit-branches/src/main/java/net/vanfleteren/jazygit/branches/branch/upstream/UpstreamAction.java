package net.vanfleteren.jazygit.branches.branch.upstream;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.ui.FeatureAction;
import net.vanfleteren.jazygit.ui.keys.Keymap;
import net.vanfleteren.jazygit.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.ui.keys.Scopes;

/**
 * The upstream options of the selected branch, and closing how it diverged.
 */
public final class UpstreamAction {

    public static final FeatureAction ACTION = new FeatureAction(
            keys -> keys.add(
                            Scopes.BRANCHES,
                            new Keymap.Binding<>(
                                    "u",
                                    "branches.help.branches.upstream",
                                    branch -> Option.of(new UpstreamMsg.Requested(branch))))
                    .add(
                            Scopes.DIVERGENCE,
                            Binding.always(
                                    Keymap.ESCAPE,
                                    "branches.help.divergence.close",
                                    UpstreamMsg.DivergenceClosed::new)),
            UpstreamDialogs::popups);

    private UpstreamAction() {}
}
