package net.vanfleteren.jazygit.keys;

import net.vanfleteren.jazygit.git.model.FileTree;
import net.vanfleteren.jazygit.git.model.Stash;
import net.vanfleteren.jazygit.keys.Keymap.Scope;

/**
 * The panels with keys of their own, and what is selected in each, which its keys act on.
 */
public final class Scopes {

    /** The selected node of the file tree. */
    public static final Scope<FileTree> FILES = new Scope<>("files", "panel.files.title");
    /** The name of the selected branch. */
    public static final Scope<String> BRANCHES = new Scope<>("branches", "panel.branches.title");
    /** The index of the selected commit. */
    public static final Scope<Integer> COMMITS = new Scope<>("commits", "panel.commits.title");
    /** The selected stash. */
    public static final Scope<Stash> STASH = new Scope<>("stash", "panel.stash.title");

    private Scopes() {
    }
}
