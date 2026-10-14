package net.vanfleteren.jazygit.feature.branch;

import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Scopes;

/**
 * The keys for working with branches.
 */
public final class BranchKeys {

    private BranchKeys() {
    }

    public static Keymap register(Keymap keys) {
        return keys
                .add(Scopes.BRANCHES,
                        Binding.<String>onSelected(" ", "help.branches.checkout", CheckoutMsg.Requested::new),
                        Binding.<String>onSelected("P", "help.branches.push", PushMsg.Requested::new),
                        Binding.<String>onSelected("p", "help.branches.pull", PullMsg.Requested::new),
                        Binding.<String>onSelected("n", "help.branches.new", NewBranchMsg.Requested::new),
                        Binding.<String>onSelected("m", "help.branches.merge", MergeMsg.Requested::new),
                        Binding.<String>onSelected("r", "help.branches.rebase", RebaseMsg.Requested::new),
                        Binding.<String>onSelected("d", "help.branches.delete", DeleteBranchMsg.Requested::new),
                        Binding.always("[", "help.branches.previousTab", BranchesTabMsg.Switched::new),
                        Binding.always("]", "help.branches.nextTab", BranchesTabMsg.Switched::new))
                .add(Scopes.REMOTE_BRANCHES,
                        Binding.<RemoteBranch>onSelected(" ", "help.remoteBranches.checkout", CheckoutMsg.RemoteRequested::new),
                        Binding.<RemoteBranch>onSelected("n", "help.branches.new", NewBranchMsg.RequestedAtRemote::new),
                        Binding.always("[", "help.branches.previousTab", BranchesTabMsg.Switched::new),
                        Binding.always("]", "help.branches.nextTab", BranchesTabMsg.Switched::new))
                .add(Scopes.COMMITS,
                        Binding.<Commit>onSelected("n", "help.commits.newBranch", commit -> new NewBranchMsg.RequestedAtCommit(commit.sha())));
    }
}
