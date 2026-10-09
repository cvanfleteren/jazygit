package net.vanfleteren.jazygit.model;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link GitInfoProvider} backed by a real repository. The working tree status comes from the
 * {@code git status} command line tool, branches and commits are read through JGit.
 */
public final class JGitInfoProvider implements GitInfoProvider, AutoCloseable {

    private static final int MAX_COMMITS = 300;

    private final Repository repository;
    private final Git git;
    private final Path workTree;

    /**
     * Opens the repository containing {@code startDir}, searching parent directories like
     * {@code git} itself does.
     *
     * @throws IllegalStateException if {@code startDir} is not inside a git repository, the
     *                               repository has no working tree, or git cannot be run
     */
    public JGitInfoProvider(Path startDir) {
        FileRepositoryBuilder builder = new FileRepositoryBuilder()
                .readEnvironment()
                .findGitDir(startDir.toFile());
        if (builder.getGitDir() == null) {
            throw new IllegalStateException("Not a git repository: " + startDir);
        }
        try {
            this.repository = builder.setMustExist(true).build();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not open git repository at " + builder.getGitDir(), e);
        }
        this.git = new Git(repository);
        try {
            this.workTree = repository.getWorkTree().toPath();
            // Fail at startup rather than on first use when git is not available.
            GitCliStatus.read(workTree);
        } catch (RuntimeException e) {
            close();
            throw e;
        }
    }

    @Override
    public String repositoryName() {
        return workTree.getFileName().toString();
    }

    @Override
    public RepoStatus status() {
        return GitCliStatus.read(workTree);
    }

    @Override
    public List<Branch> branches() {
        try {
            String fullBranch = repository.getFullBranch();
            List<Branch> branches = new ArrayList<>();
            for (Ref ref : git.branchList().call()) {
                branches.add(new Branch(Repository.shortenRefName(ref.getName()),
                        ref.getName().equals(fullBranch)));
            }
            return List.copyOf(branches);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read branches", e);
        } catch (GitAPIException e) {
            throw new IllegalStateException("Could not read branches", e);
        }
    }

    @Override
    public List<Commit> commits() {
        try {
            if (repository.resolve(Constants.HEAD) == null) {
                return List.of();
            }
            List<Commit> commits = new ArrayList<>();
            for (RevCommit c : git.log().setMaxCount(MAX_COMMITS).call()) {
                PersonIdent author = c.getAuthorIdent();
                String date = LocalDate.ofInstant(author.getWhenAsInstant(), author.getZoneId()).toString();
                commits.add(new Commit(c.abbreviate(7).name(), author.getName(), date,
                        c.getShortMessage(), c.getFullMessage()));
            }
            return List.copyOf(commits);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read commit log", e);
        } catch (GitAPIException e) {
            throw new IllegalStateException("Could not read commit log", e);
        }
    }

    @Override
    public void close() {
        git.close();
        repository.close();
    }
}
