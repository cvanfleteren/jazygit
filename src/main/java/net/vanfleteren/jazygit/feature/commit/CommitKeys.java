package net.vanfleteren.jazygit.feature.commit;

import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.FileTree;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Scopes;

/**
 * The keys for committing, amending, rewording, squashing, fixing up and resetting.
 */
public final class CommitKeys {

    private CommitKeys() {
    }

    public static Keymap register(Keymap keys) {
        return keys
                .add(Scopes.FILES,
                        Binding.<FileTree>always("c", "help.files.commit", CommitMsg.Requested::new),
                        Binding.<FileTree>always("A", "help.files.amend", AmendMsg.Requested::new))
                .add(Scopes.COMMITS,
                        Binding.<Commit>onSelected("r", "help.commits.reword", commit -> new RewordMsg.Requested(commit.sha())),
                        Binding.<Commit>onSelected("s", "help.commits.squash", commit -> new SquashMsg.Requested(commit.sha())),
                        Binding.<Commit>onSelected("f", "help.commits.fixup", commit -> new FixupMsg.Requested(commit.sha())),
                        Binding.<Commit>onSelected("g", "help.commits.reset", commit -> new ResetMsg.Requested(commit.sha())));
    }
}
