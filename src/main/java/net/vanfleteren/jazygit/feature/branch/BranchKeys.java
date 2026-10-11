package net.vanfleteren.jazygit.feature.branch;

import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;

/**
 * The keys for working with branches.
 */
public final class BranchKeys {

    private BranchKeys() {
    }

    public static void register(Keymap.Builder keys) {
        keys.add(Scopes.BRANCHES,
                Binding.<String>onSelected(" ", "help.branches.checkout", CheckoutMsg.Requested::new),
                Binding.<String>onSelected("P", "help.branches.push", PushMsg.Requested::new),
                Binding.<String>onSelected("n", "help.branches.new", NewBranchMsg.Requested::new),
                Binding.<String>onSelected("r", "help.branches.rebase", RebaseMsg.Requested::new),
                Binding.<String>onSelected("d", "help.branches.delete", DeleteBranchMsg.Requested::new));
    }
}
