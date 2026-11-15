package net.vanfleteren.jazygit.changes.diff;

import net.vanfleteren.jazygit.state.diff.Changes;
import net.vanfleteren.jazygit.ui.keys.FeatureAction;
import net.vanfleteren.jazygit.ui.keys.Keymap;
import net.vanfleteren.jazygit.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.ui.keys.Scopes;

/**
 * Viewing the files the selected commit or stash changed, and going back to the files of the working tree.
 */
public final class CommitFilesAction {

    private static final String KEY = "F";

    public static final FeatureAction ACTION = FeatureAction.ofKeys(keys -> keys.add(
                    Scopes.COMMITS,
                    Binding.onSelected(
                            KEY,
                            "help.commits.files",
                            commit -> new CommitFilesMsg.Opened(
                                    new Changes.OfCommit(commit.sha()), Scopes.COMMITS.pane())))
            .add(
                    Scopes.DIVERGENCE,
                    Binding.onSelected(
                            KEY,
                            "help.commits.files",
                            commit -> new CommitFilesMsg.Opened(
                                    new Changes.OfCommit(commit.sha()), Scopes.BRANCHES.pane())))
            .add(
                    Scopes.BRANCH_COMMITS,
                    Binding.onSelected(
                            KEY,
                            "help.commits.files",
                            commit -> new CommitFilesMsg.Opened(
                                    new Changes.OfCommit(commit.sha()), Scopes.BRANCHES.pane())))
            .add(
                    Scopes.STASH,
                    Binding.onSelected(
                            KEY,
                            "help.stash.files",
                            stash -> new CommitFilesMsg.Opened(new Changes.OfStash(stash.sha()), Scopes.STASH.pane())))
            .add(
                    Scopes.COMMIT_FILES,
                    Binding.always(Keymap.ESCAPE, "help.commitFiles.close", CommitFilesMsg.Closed::new))
            // The review threads of a pull request (ADR 55); nothing for a commit or a stash.
            // All files of the commit the files end at (ADR 128), as `a` lists those of the working tree (ADR 108).
            .add(Scopes.COMMIT_FILES, Binding.always("a", "help.commitFiles.allFiles", CommitFilesMsg.AllToggled::new))
            .add(Scopes.COMMIT_FILES, Binding.always("c", "help.commitFiles.listed", CommitFilesMsg.ListedCycled::new))
            .add(
                    Scopes.COMMIT_FILES,
                    Binding.always("R", "help.commitFiles.refresh", CommitFilesMsg.RemarksRefreshed::new)));

    private CommitFilesAction() {}
}
