package net.vanfleteren.jazygit.feature.branch;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;

/**
 * The keys for working with branches.
 */
public final class BranchKeys {

    private BranchKeys() {}

    public static Keymap register(Keymap keys) {
        return keys.add(
                        Scopes.BRANCHES,
                        Binding.onSelected(" ", "help.branches.checkout", CheckoutMsg.Requested::new),
                        Binding.onSelected("P", "help.branches.push", PushMsg.Requested::new),
                        Binding.onSelected("p", "help.branches.pull", PullMsg.Requested::new),
                        Binding.onSelected("n", "help.branches.new", NewBranchMsg.Requested::new),
                        Binding.onSelected("m", "help.branches.merge", MergeMsg.Requested::new),
                        Binding.onSelected("r", "help.branches.rebase", RebaseMsg.Requested::new),
                        Binding.onSelected("R", "help.branches.rename", RenameBranchMsg.Requested::new),
                        Binding.onSelected("d", "help.branches.delete", DeleteBranchMsg.Requested::new),
                        new Binding<>(
                                "u", "help.branches.upstream", branch -> Option.of(new UpstreamMsg.Requested(branch))),
                        Binding.always("[", "help.branches.previousTab", () -> new BranchesTabMsg.Switched(-1)),
                        Binding.always("]", "help.branches.nextTab", () -> new BranchesTabMsg.Switched(1)))
                .add(
                        Scopes.REMOTE_BRANCHES,
                        Binding.onSelected(" ", "help.remoteBranches.checkout", CheckoutMsg.RemoteRequested::new),
                        Binding.onSelected("n", "help.branches.new", NewBranchMsg.RequestedAtRemote::new),
                        Binding.onSelected("d", "help.remoteBranches.delete", DeleteBranchMsg.RemoteRequested::new),
                        Binding.always("[", "help.branches.previousTab", () -> new BranchesTabMsg.Switched(-1)),
                        Binding.always("]", "help.branches.nextTab", () -> new BranchesTabMsg.Switched(1)))
                .add(
                        Scopes.DIVERGENCE,
                        Binding.always(Keymap.ESCAPE, "help.divergence.close", UpstreamMsg.DivergenceClosed::new),
                        Binding.always("[", "help.branches.previousTab", () -> new BranchesTabMsg.Switched(-1)),
                        Binding.always("]", "help.branches.nextTab", () -> new BranchesTabMsg.Switched(1)))
                .add(
                        Scopes.COMMITS,
                        Binding.onSelected(
                                "n",
                                "help.commits.newBranch",
                                commit -> new NewBranchMsg.RequestedAtCommit(commit.sha())));
    }
}
