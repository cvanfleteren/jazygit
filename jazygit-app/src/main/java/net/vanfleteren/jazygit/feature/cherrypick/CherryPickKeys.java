package net.vanfleteren.jazygit.feature.cherrypick;

import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;

/**
 * The keys for copying a commit and cherry-picking it onto another branch.
 */
public final class CherryPickKeys {

    private CherryPickKeys() {}

    public static Keymap register(Keymap keys) {
        return keys.add(
                Scopes.COMMITS,
                new Binding<Commit>(
                        "C",
                        "help.commits.cherryPickCopy",
                        selection -> selection
                                .filter(commit -> !commit.isMerge())
                                .map(commit -> new CherryPickMsg.Copied(commit.sha()))),
                Binding.always("V", "help.commits.cherryPickPaste", CherryPickMsg.Requested::new));
    }
}
