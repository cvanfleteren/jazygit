package net.vanfleteren.jazygit.features.forgejo.checks;

import java.util.function.UnaryOperator;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Scope;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;

/**
 * The keys of the checks popup on Forgejo (ADR 195), those of GitHub's, as bindings, so that the keys other features
 * add to a failed build are among them (ADR 161). Scrolling and closing stay the popup's own.
 */
public final class ChecksAction {

    /**
     * The checks popup, whose keys act on what it shows.
     */
    public static final Scope<ForgejoChecksPopup> SCOPE = new Scope<>("forgejo-checks", "forgejo.dialog.checks.keys");

    /**
     * The keys of the popup, which host those of a failed build.
     */
    public static final UnaryOperator<Keymap> KEYS = keys -> keys.add(
                    SCOPE,
                    Binding.always("r", "forgejo.help.checks.rerun", ChecksMsg.RerunRequested::new),
                    Binding.always("R", "forgejo.help.checks.refresh", ChecksMsg.RefreshChosen::new),
                    Binding.always("n", "forgejo.help.checks.next", () -> new ChecksMsg.Stepped(1)),
                    Binding.always("N", "forgejo.help.checks.previous", () -> new ChecksMsg.Stepped(-1)),
                    Binding.always("e", "forgejo.help.checks.log", ChecksMsg.LogRequested::new))
            .host(SCOPE, Scopes.FAILED_BUILD);

    private ChecksAction() {}
}
