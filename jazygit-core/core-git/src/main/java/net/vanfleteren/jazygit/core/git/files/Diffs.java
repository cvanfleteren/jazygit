package net.vanfleteren.jazygit.core.git.files;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.core.git.branches.Remotes;
import net.vanfleteren.jazygit.core.git.model.CommitFile;
import net.vanfleteren.jazygit.core.git.model.DiffOptions;
import net.vanfleteren.jazygit.core.git.model.DiffText;
import net.vanfleteren.jazygit.core.git.model.FileContent;
import net.vanfleteren.jazygit.core.git.model.FileEntry;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.repo.PullRequestCommits;
import net.vanfleteren.jazygit.core.repo.RepoPath;
import net.vanfleteren.jazygit.core.repo.Sha;

/**
 * Reading what changed, as git show and git diff show it: a commit, a pull request or a branch against its base,
 * and the working tree and the index (ADR 69).
 */
public interface Diffs {

    // A single commit, as git show shows it.

    /**
     * What the commit {@code sha} changed, like {@code git show}: the changed files, a summary of the
     * changes, a blank line and the diff, read with {@code options}. Long output is cut off.
     */
    Either<GitError, DiffText> commitChanges(Sha sha, DiffOptions options);

    /**
     * The files the commit {@code sha} changed, those of a merge against its first parent.
     */
    Either<GitError, List<CommitFile>> commitFiles(Sha sha);

    /**
     * The paths of all files of the commit {@code sha}, whether it changed them or not, sorted (ADR 128).
     */
    Either<GitError, List<RepoPath>> treeFiles(Sha sha);

    /**
     * What the file at {@code path} holds in the commit {@code sha}, as a file without changes shows it (ADR 128).
     */
    Either<GitError, FileContent> treeContent(Sha sha, RepoPath path);

    /**
     * The text of the file at {@code path} in the commit {@code sha}, whole, as a small file of settings is read, like
     * a workflow of GitHub Actions (ADR 131).
     */
    Either<GitError, String> treeText(Sha sha, RepoPath path);

    /**
     * What the commit {@code sha} changed in {@code files}, like {@code git show -- <paths>}: only the diff, a merge
     * against its first parent, read with {@code options}. Long output is cut off.
     */
    Either<GitError, DiffText> commitFileChanges(Sha sha, List<CommitFile> files, DiffOptions options);

    // What a pull request on GitHub changes, as GitHub shows it (ADR 55).

    /**
     * What the pull request of {@code commits} changes in {@code files}, read with {@code options}, as
     * {@link Remotes#pullRequestFiles} reads them, which fetched its commits. Long output is cut off.
     */
    Either<GitError, DiffText> pullRequestFileChanges(
            PullRequestCommits commits, List<CommitFile> files, DiffOptions options);

    // What a branch changes against its base, as a pull request of it would (ADR 61).

    /**
     * The files {@code tip} changes against the merge base of it and {@code base}, as
     * {@link Remotes#pullRequestFiles} reads those of a pull request; nothing is fetched.
     */
    Either<GitError, List<CommitFile>> rangeFiles(Sha base, Sha tip);

    /**
     * What {@code tip} changes in {@code files} against the merge base of it and {@code base}, read with
     * {@code options}. Long output is cut off.
     */
    Either<GitError, DiffText> rangeFileChanges(Sha base, Sha tip, List<CommitFile> files, DiffOptions options);

    // The changes in the working tree and the index, as git diff shows them.

    /**
     * The staged and the unstaged diff of {@code files}, read with {@code options}.
     */
    Either<GitError, net.vanfleteren.jazygit.core.git.model.Diffs> uncommitted(
            List<FileEntry> files, DiffOptions options);
}
