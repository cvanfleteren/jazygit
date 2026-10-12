package net.vanfleteren.jazygit.feature.commit;

import net.vanfleteren.jazygit.feature.branch.NewBranchMsg;
import net.vanfleteren.jazygit.git.model.FileTree;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;

/**
 * The keys for committing, amending, rewording and squashing.
 */
public final class CommitKeys {

    private CommitKeys() {
    }

    public static void register(Keymap.Builder keys) {
        keys.add(Scopes.FILES,
                Binding.<FileTree>always("c", "help.files.commit", CommitMsg.Requested::new),
                Binding.<FileTree>always("A", "help.files.amend", AmendMsg.Requested::new));
        keys.add(Scopes.COMMITS,
                Binding.<Integer>onSelected("r", "help.commits.reword", RewordMsg.Requested::new),
                Binding.<Integer>onSelected("n", "help.commits.newBranch", NewBranchMsg.RequestedAtCommit::new),
                Binding.<Integer>onSelected("s", "help.commits.squash", SquashMsg.Requested::new));
    }
}
