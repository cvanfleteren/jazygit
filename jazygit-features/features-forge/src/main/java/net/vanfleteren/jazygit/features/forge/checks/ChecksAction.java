package net.vanfleteren.jazygit.features.forge.checks;

import java.util.function.UnaryOperator;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Scope;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;

/**
 * The keys of the checks popup (ADR 32, 135, 160), as bindings, so that the keys other features add to a failed build
 * are among them (ADR 161). Scrolling and closing stay the popup's own. Each forge's dialog has a scope of its own, so
 * that the keys of a forge that is off are not listed beside those of the one that is on.
 */
public final class ChecksAction {

    private ChecksAction() {}

    /**
     * The scope {@code id} of a forge's checks dialog, whose keys act on what it shows.
     */
    public static Scope<ChecksPopup> scope(String id) {
        return new Scope<>(id, "forge.dialog.checks.keys");
    }

    /**
     * The keys of the popup in {@code scope}, which host those of a failed build.
     */
    public static UnaryOperator<Keymap> keys(Scope<ChecksPopup> scope) {
        return keys -> keys.add(
                        scope,
                        Binding.always("r", "forge.help.checks.rerun", ChecksMsg.RerunRequested::new),
                        Binding.always("R", "forge.help.checks.refresh", ChecksMsg.RefreshChosen::new),
                        Binding.always("n", "forge.help.checks.next", () -> new ChecksMsg.Stepped(1)),
                        Binding.always("N", "forge.help.checks.previous", () -> new ChecksMsg.Stepped(-1)),
                        Binding.always("e", "forge.help.checks.log", ChecksMsg.LogRequested::new))
                .host(scope, Scopes.FAILED_BUILD);
    }
}
