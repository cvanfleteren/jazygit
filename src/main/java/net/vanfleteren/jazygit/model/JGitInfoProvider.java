package net.vanfleteren.jazygit.model;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * {@link GitInfoProvider} backed by a real repository. The working tree status comes from the
 * {@code git status} command line tool, branches and commits are read through JGit,
 * and checkouts go through the {@code git checkout} command line tool.
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
            Map<String, Integer> modified = new HashMap<>();
            try (RevWalk walk = new RevWalk(repository)) {
                for (Ref ref : git.branchList().call()) {
                    modified.put(ref.getObjectId().name(), walk.parseCommit(ref.getObjectId()).getCommitTime());
                }
            }
            Optional<String> defaultBranch = defaultBranch();
            List<Branch> branches = new ArrayList<>();
            for (Ref ref : git.branchList().call()) {
                branches.add(new Branch(Repository.shortenRefName(ref.getName()),
                        ref.getName().equals(fullBranch), ref.getObjectId().name(),
                        Instant.ofEpochSecond(modified.get(ref.getObjectId().name())),
                        hasRemote(Repository.shortenRefName(ref.getName())),
                        defaultBranch.equals(Optional.of(Repository.shortenRefName(ref.getName())))));
            }
            // The checked out branch first, then the most recently committed to.
            return branches.stream()
                    .sorted(Comparator.comparing(Branch::current).reversed()
                            .thenComparing(Comparator.comparingInt((Branch b) -> modified.get(b.tipOid())).reversed())
                            .thenComparing(Branch::name))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read branches", e);
        } catch (GitAPIException e) {
            throw new IllegalStateException("Could not read branches", e);
        }
    }

    @Override
    public List<Commit> commits() {
        try {
            ObjectId head = repository.resolve(Constants.HEAD);
            return head == null ? List.of() : log(head);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read commit log", e);
        }
    }

    @Override
    public List<Commit> log(String branch) {
        try {
            ObjectId tip = repository.resolve(Constants.R_HEADS + branch);
            if (tip == null) {
                throw new IllegalStateException("Unknown branch: " + branch);
            }
            return log(tip);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the log of " + branch, e);
        }
    }

    private List<Commit> log(ObjectId start) throws IOException {
        try {
            return StreamSupport.stream(git.log().add(start).setMaxCount(MAX_COMMITS).call().spliterator(), false)
                    .map(JGitInfoProvider::toCommit)
                    .toList();
        } catch (GitAPIException e) {
            throw new IllegalStateException("Could not read commit log", e);
        }
    }

    private static Commit toCommit(RevCommit c) {
        PersonIdent author = c.getAuthorIdent();
        return new Commit(c.abbreviate(7).name(), author.getName(), author.getEmailAddress(),
                author.getWhenAsInstant(), c.getShortMessage(), extendedMessage(c.getFullMessage()));
    }

    /**
     * The commit message after the subject paragraph, or an empty string if there is none.
     */
    static String extendedMessage(String fullMessage) {
        String[] parts = fullMessage.split("\\R\\s*\\R", 2);
        return parts.length < 2 ? "" : parts[1].strip();
    }

    @Override
    public void checkout(String branch) {
        GitCliCheckout.checkout(workTree, branch);
    }

    /**
     * The main branch: the one {@code origin/HEAD} points to, as set by a clone. Without it, the
     * first of {@code main} and {@code master} that exists.
     */
    private Optional<String> defaultBranch() throws IOException {
        String prefix = "refs/remotes/origin/";
        Ref head = repository.exactRef(prefix + "HEAD");
        if (head != null && head.isSymbolic() && head.getTarget().getName().startsWith(prefix)) {
            return Optional.of(head.getTarget().getName().substring(prefix.length()));
        }
        return Stream.of("main", "master")
                .filter(name -> {
                    try {
                        return repository.exactRef("refs/heads/" + name) != null;
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })
                .findFirst();
    }

    /**
     * Whether the remote-tracking ref of {@code branch} exists. The remote is the one
     * {@link GitCliBranch#deleteRemote} pushes to: the configured one, or {@code origin}.
     */
    private boolean hasRemote(String branch) throws IOException {
        String remote = repository.getConfig().getString("branch", branch, "remote");
        return repository.exactRef("refs/remotes/" + (remote == null ? "origin" : remote) + "/" + branch) != null;
    }

    @Override
    public void createBranch(String name, String startPoint) {
        GitCliCheckout.createBranch(workTree, name, startPoint);
    }

    @Override
    public void deleteBranch(String name, boolean local, boolean remote) {
        if (remote) {
            GitCliBranch.deleteRemote(workTree, name);
        }
        if (local) {
            GitCliBranch.deleteLocal(workTree, name);
        }
    }

    @Override
    public void stage(List<String> paths) {
        GitCliIndex.add(workTree, paths);
    }

    @Override
    public void unstageNew(List<String> paths) {
        GitCliIndex.removeCached(workTree, paths);
    }

    @Override
    public void unstage(List<String> paths) {
        GitCliIndex.reset(workTree, paths);
    }

    @Override
    public Diffs diff(List<FileEntry> files) {
        return GitCliDiff.diff(workTree, files);
    }

    @Override
    public void close() {
        git.close();
        repository.close();
    }
}
