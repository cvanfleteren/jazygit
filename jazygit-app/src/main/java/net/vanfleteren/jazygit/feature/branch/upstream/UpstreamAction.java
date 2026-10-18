package net.vanfleteren.jazygit.feature.branch.upstream;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.FeatureAction;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;

/**
 * The upstream options of the selected branch, and closing how it diverged.
 */
public final class UpstreamAction {

    public static final FeatureAction ACTION = new FeatureAction(
            keys -> keys.add(
                            Scopes.BRANCHES,
                            new Keymap.Binding<>(
                                    "u",
                                    "help.branches.upstream",
                                    branch -> Option.of(new UpstreamMsg.Requested(branch))))
                    .add(
                            Scopes.DIVERGENCE,
                            Binding.always(Keymap.ESCAPE, "help.divergence.close", UpstreamMsg.DivergenceClosed::new)),
            UpstreamDialogs::popups);

    private UpstreamAction() {}
}
