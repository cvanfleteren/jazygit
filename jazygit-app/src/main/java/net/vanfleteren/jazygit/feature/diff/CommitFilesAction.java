package net.vanfleteren.jazygit.feature.diff;

import net.vanfleteren.jazygit.feature.FeatureAction;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.CommitFilesView.Changes;

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
                            commit ->
                                    new CommitFilesMsg.Opened(new Changes.OfCommit(commit.sha()), Scopes.COMMITS.id())))
            .add(
                    Scopes.DIVERGENCE,
                    Binding.onSelected(
                            KEY,
                            "help.commits.files",
                            commit -> new CommitFilesMsg.Opened(
                                    new Changes.OfCommit(commit.sha()), Scopes.BRANCHES.id())))
            .add(
                    Scopes.BRANCH_COMMITS,
                    Binding.onSelected(
                            KEY,
                            "help.commits.files",
                            commit -> new CommitFilesMsg.Opened(
                                    new Changes.OfCommit(commit.sha()), Scopes.BRANCHES.id())))
            .add(
                    Scopes.STASH,
                    Binding.onSelected(
                            KEY,
                            "help.stash.files",
                            stash -> new CommitFilesMsg.Opened(new Changes.OfStash(stash.sha()), Scopes.STASH.id())))
            .add(
                    Scopes.COMMIT_FILES,
                    Binding.always(Keymap.ESCAPE, "help.commitFiles.close", CommitFilesMsg.Closed::new))
            // The review threads of a pull request (ADR 55); nothing for a commit or a stash.
            .add(Scopes.COMMIT_FILES, Binding.always("c", "help.commitFiles.listed", CommitFilesMsg.ListedCycled::new))
            .add(
                    Scopes.COMMIT_FILES,
                    Binding.always("R", "help.commitFiles.refresh", CommitFilesMsg.RemarksRefreshed::new)));

    private CommitFilesAction() {}
}
