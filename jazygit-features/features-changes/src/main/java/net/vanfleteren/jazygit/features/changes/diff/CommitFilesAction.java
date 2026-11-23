package net.vanfleteren.jazygit.features.changes.diff;

import net.vanfleteren.jazygit.kernel.state.diff.Changes;
import net.vanfleteren.jazygit.kernel.ui.FeatureAction;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;

/**
 * Viewing the files the selected commit or stash changed, and going back to the files of the working tree.
 */
public final class CommitFilesAction {

    // Enter opens the files, and locks the files pane to the list it was pressed in, until Escape (ADR 149).
    private static final String KEY = Keymap.ENTER;

    public static final FeatureAction ACTION = FeatureAction.ofKeys(keys -> keys.add(
                    Scopes.COMMITS,
                    Binding.onSelected(
                            KEY,
                            "changes.help.commits.files",
                            commit -> new CommitFilesMsg.Opened(
                                    new Changes.OfCommit(commit.sha()), Scopes.COMMITS.pane())))
            // The reflog is a tab of the commits pane (ADR 186).
            .add(
                    Scopes.REFLOG,
                    Binding.onSelected(
                            KEY,
                            "changes.help.commits.files",
                            entry -> new CommitFilesMsg.Opened(
                                    new Changes.OfCommit(entry.sha()), Scopes.COMMITS.pane())))
            .add(
                    Scopes.DIVERGENCE,
                    Binding.onSelected(
                            KEY,
                            "changes.help.commits.files",
                            commit -> new CommitFilesMsg.Opened(
                                    new Changes.OfCommit(commit.sha()), Scopes.BRANCHES.pane())))
            .add(
                    Scopes.BRANCH_COMMITS,
                    Binding.onSelected(
                            KEY,
                            "changes.help.commits.files",
                            commit -> new CommitFilesMsg.Opened(
                                    new Changes.OfCommit(commit.sha()), Scopes.BRANCHES.pane())))
            // The files of a branch, as a part knows them, as its pull request's, else against its base (ADR 61, 171).
            .add(
                    Scopes.BRANCHES,
                    Binding.onSelected("F", "changes.help.branches.files", CommitFilesMsg.BranchFilesRequested::new))
            .add(
                    Scopes.STASH,
                    Binding.onSelected(
                            KEY,
                            "changes.help.stash.files",
                            stash -> new CommitFilesMsg.Opened(new Changes.OfStash(stash.sha()), Scopes.STASH.pane())))
            .add(
                    Scopes.COMMIT_FILES,
                    Binding.always(Keymap.ESCAPE, "changes.help.commitFiles.close", CommitFilesMsg.Closed::new))
            // All files of the commit the files end at (ADR 128), as `a` lists those of the working tree (ADR 108).
            .add(
                    Scopes.COMMIT_FILES,
                    Binding.always("a", "changes.help.commitFiles.allFiles", CommitFilesMsg.AllToggled::new))
            // Which files are listed, by the remarks a part states for them (ADR 57, 171).
            .add(
                    Scopes.COMMIT_FILES,
                    Binding.always("c", "changes.help.commitFiles.listed", CommitFilesMsg.ListedCycled::new)));

    private CommitFilesAction() {}
}
