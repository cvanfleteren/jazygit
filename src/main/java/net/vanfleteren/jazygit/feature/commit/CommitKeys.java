package net.vanfleteren.jazygit.feature.commit;

import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;

/**
 * The keys for committing, amending (the last or any commit), rewording, squashing, fixing up, dropping and resetting.
 */
public final class CommitKeys {

    private CommitKeys() {}

    public static Keymap register(Keymap keys) {
        return keys.add(
                        Scopes.FILES,
                        Binding.always("c", "help.files.commit", CommitMsg.Requested::new),
                        Binding.always("A", "help.files.amend", AmendMsg.Requested::new))
                .add(
                        Scopes.COMMITS,
                        Binding.onSelected(
                                "A", "help.commits.amend", commit -> new AmendCommitMsg.Requested(commit.sha())),
                        Binding.onSelected("r", "help.commits.reword", commit -> new RewordMsg.Requested(commit.sha())),
                        Binding.onSelected("s", "help.commits.squash", commit -> new SquashMsg.Requested(commit.sha())),
                        Binding.onSelected("f", "help.commits.fixup", commit -> new FixupMsg.Requested(commit.sha())),
                        Binding.onSelected("d", "help.commits.drop", commit -> new DropMsg.Requested(commit.sha())),
                        Binding.onSelected("g", "help.commits.reset", commit -> new ResetMsg.Requested(commit.sha())));
    }
}
