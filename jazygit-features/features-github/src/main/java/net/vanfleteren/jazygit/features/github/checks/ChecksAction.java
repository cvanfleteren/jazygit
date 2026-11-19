package net.vanfleteren.jazygit.features.github.checks;

import java.util.function.UnaryOperator;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Scope;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;

/**
 * The keys of the checks popup (ADR 32, 135), as bindings, so that the keys other features add to a failed build are
 * among them (ADR 161). Scrolling and closing stay the popup's own.
 */
public final class ChecksAction {

    /**
     * The checks popup, whose keys act on what it shows.
     */
    public static final Scope<ChecksPopup> SCOPE = new Scope<>("checks", "github.dialog.checks.keys");

    /**
     * The keys of the popup, which host those of a failed build.
     */
    public static final UnaryOperator<Keymap> KEYS = keys -> keys.add(
                    SCOPE,
                    Binding.always("r", "github.help.checks.rerun", ChecksMsg.RerunRequested::new),
                    Binding.always("R", "github.help.checks.refresh", ChecksMsg.RefreshChosen::new))
            .host(SCOPE, Scopes.FAILED_BUILD);

    private ChecksAction() {}
}
