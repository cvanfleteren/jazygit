package net.vanfleteren.jazygit.state.load;

/**
 * A load that keeps what it reads in a {@link Target}: what it read goes there, so does that it failed, and a
 * tick reads it again while it shows as failed there. The loads that are not targeted keep what they read in a
 * way of their own, which {@link LoadUpdate} spells out for each.
 *
 * @param <T> what the load reads
 */
public sealed interface Targeted<T> extends Load
        permits LoadStatus,
                LoadStashes,
                LoadWorktrees,
                LoadCommits,
                SearchCommits,
                LoadBranchLog,
                LoadDivergence,
                LoadCommitDetail,
                LoadStashDetail,
                LoadCommitFiles,
                LoadCommitFileDiff,
                LoadFileDiff,
                ReadWholeFile,
                ReadVersion {

    Target<T> target();
}
