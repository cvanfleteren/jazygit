package net.vanfleteren.jazygit.core.git.model;

import net.vanfleteren.jazygit.core.repo.RepoPath;
import net.vanfleteren.jazygit.core.repo.Sha;

/**
 * The revision a version of a file is blamed at, the one its lines are of (ADR 147), and the path of the file there.
 */
public sealed interface BlameAt {

    /**
     * The path of the file blamed, in the revision it is blamed at.
     */
    RepoPath path();

    /**
     * The file in the commit {@code sha}, or in its {@code parent}th parent: {@code <sha>^<parent>}. Parent 0 is the
     * commit itself; a stash's first parent is what it was made on, and its third its untracked files.
     */
    record InCommit(Sha sha, int parent, RepoPath path) implements BlameAt {

        /**
         * The file in the commit {@code sha} itself.
         */
        public InCommit(Sha sha, RepoPath path) {
            this(sha, 0, path);
        }
    }

    /**
     * The file at the merge base of {@code tip} and {@code base}: the original of the files of a branch or a pull
     * request, which are read as {@code <base>...<tip>}.
     */
    record AtMergeBase(Sha base, Sha tip, RepoPath path) implements BlameAt {}

    /**
     * The file at {@code HEAD}: the original of the staged changes.
     */
    record AtHead(RepoPath path) implements BlameAt {}

    /**
     * The file in the index, blamed as if it were committed on {@code HEAD}.
     */
    record InIndex(RepoPath path) implements BlameAt {}

    /**
     * The file on disk, blamed as if it were committed on {@code HEAD}.
     */
    record OnDisk(RepoPath path) implements BlameAt {}
}
