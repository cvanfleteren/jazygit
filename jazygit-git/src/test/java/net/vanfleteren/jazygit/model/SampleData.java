package net.vanfleteren.jazygit.model;

import io.vavr.collection.List;
import io.vavr.control.Either;
import java.time.Instant;
import net.vanfleteren.jazygit.git.Git;
import net.vanfleteren.jazygit.git.StubGit;
import net.vanfleteren.jazygit.git.branches.Branches;
import net.vanfleteren.jazygit.git.branches.StubBranches;
import net.vanfleteren.jazygit.git.files.Status;
import net.vanfleteren.jazygit.git.files.StubDiffs;
import net.vanfleteren.jazygit.git.files.StubStatus;
import net.vanfleteren.jazygit.git.history.History;
import net.vanfleteren.jazygit.git.history.StubHistory;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.git.process.GitError;

/**
 * Fixed sample data used by tests, so they don't depend on the state of a real repository.
 */
public final class SampleData {

    private static final String ADA = "Ada Lovelace <ada@example.com>";
    private static final String GRACE = "Grace Hopper <grace@example.com>";
    private static final String LINUS = "Linus Torvalds <linus@example.com>";
    private static final List<Commit> MAIN = List.of(
            commit(
                    "a1b2c3d",
                    ADA,
                    "2026-10-09T12:30:00Z",
                    "Add initial TUI layout",
                    "Adds the JazygitApp entry point and the Files/Branches/Commits panels."),
            commit(
                    "b2c3d4e",
                    ADA,
                    "2026-10-08T09:12:00Z",
                    "Scaffold Maven project",
                    "Adds pom.xml with the TamboUI dependencies."),
            commit("c3d4e5f", GRACE, "2026-10-07T17:45:00Z", "Wire content panel", ""),
            commit(
                    "d4e5f6a",
                    GRACE,
                    "2026-10-06T11:00:00Z",
                    "Implement focus cycling",
                    "Tab and Shift+Tab move the focus between the left panes."),
            commit("e5f6a7b", LINUS, "2026-10-05T08:20:00Z", "Initial commit", ""),
            commit("f6a7b8c", LINUS, "2026-10-04T19:05:00Z", "Project setup", "Adds .gitignore and LICENSE."));

    /**
     * @param author name and email, as {@code "Name <email>"}
     */
    private static Commit commit(String sha, String author, String time, String message, String body) {
        int lt = author.indexOf(" <");
        return Fixtures.commit(
                new Sha(sha),
                author.substring(0, lt),
                author.substring(lt + 2, author.length() - 1),
                Instant.parse(time),
                message,
                body);
    }

    private static String sample(List<FileEntry> files, java.util.function.Predicate<FileEntry> filter) {
        return files.filter(filter)
                .map(f -> "diff --git a/" + f.path() + " b/" + f.path() + "\n@@ -1 +1 @@\n-old\n+new\n")
                .mkString();
    }

    private SampleData() {}

    /**
     * The sample repository, {@code jazygit}, as a {@link Git} whose status, branches, history and diffs are the
     * samples; its other roles are stubs.
     */
    public static Git git() {
        return StubGit.of(StubGit.WORKTREE, "jazygit")
                .withStatus(status())
                .withBranches(branches())
                .withHistory(history())
                .withDiffs(diffs());
    }

    private static Status status() {
        return new StubStatus() {
            @Override
            public Either<GitError, RepoStatus> read() {
                return Either.right(new RepoStatus(
                        Head.onBranch(new BranchName("main"), new Sha("a1b2c3d4e5f60718293a4b5c6d7e8f9012345678")),
                        List.of(
                                Fixtures.fileEntry(
                                        "src/main/java/net/vanfleteren/jazygit/JazygitApp.java", ChangeType.MODIFIED),
                                Fixtures.fileEntry(
                                        "src/main/java/net/vanfleteren/jazygit/ui/ContentPanel.java", ChangeType.ADDED),
                                Fixtures.fileEntry(
                                        "src/main/java/net/vanfleteren/jazygit/model/SampleData.java",
                                        ChangeType.ADDED),
                                Fixtures.fileEntry("pom.xml", ChangeType.MODIFIED),
                                Fixtures.fileEntry("README.md", ChangeType.DELETED),
                                Fixtures.fileEntry("notes.txt", ChangeType.UNTRACKED))));
            }
        };
    }

    private static Branches branches() {
        return new StubBranches() {
            @Override
            public Either<GitError, List<Branch>> local() {
                return Either.right(List.of(
                        Fixtures.branch(
                                new BranchName("main"),
                                true,
                                new Sha("a1b2c3d4e5f60718293a4b5c6d7e8f9012345678"),
                                Instant.EPOCH,
                                true,
                                true),
                        Fixtures.branch(
                                new BranchName("feature/initial-layout"),
                                false,
                                new Sha("0a1b2c3d4e5f60718293a4b5c6d7e8f901234567"),
                                Instant.EPOCH,
                                true),
                        Fixtures.branch(
                                new BranchName("feature/jgit-backend"),
                                false,
                                new Sha("1a2b3c4d5e6f708192a3b4c5d6e7f80912345678")),
                        Fixtures.branch(
                                new BranchName("bugfix/focus-cycling"),
                                false,
                                new Sha("c3d4e5f60718293a4b5c6d7e8f9012345678a1b2"))));
            }
        };
    }

    private static History history() {
        return new StubHistory() {
            @Override
            public Either<GitError, List<Commit>> commits(Sha head) {
                return headLog();
            }

            @Override
            public Either<GitError, List<Commit>> log(BranchRef branch) {
                return SampleData.log(branch);
            }
        };
    }

    /**
     * The log of HEAD, wherever it is.
     */
    public static Either<GitError, List<Commit>> headLog() {
        return Either.right(MAIN);
    }

    private static Either<GitError, List<Commit>> log(BranchRef branch) {
        return switch (branch.name()) {
            case "main" -> Either.right(MAIN);
            case "feature/initial-layout" ->
                Either.right(List.of(
                        commit("0a1b2c3", ADA, "2026-10-09T16:05:00Z", "Split the screen into panes", ""),
                        commit(
                                "9f8e7d6",
                                ADA,
                                "2026-10-09T15:40:00Z",
                                "Add a placeholder status bar",
                                "Shows the repository name until the real status is loaded.")));
            case "feature/jgit-backend" ->
                Either.right(List.of(commit(
                        "1a2b3c4",
                        LINUS,
                        "2026-10-08T21:15:00Z",
                        "Read branches through JGit",
                        "Replaces the hard-coded branch list.\nThe current branch is marked.")));
            case "bugfix/focus-cycling" -> Either.right(MAIN.drop(2));
            default -> Either.left(new GitError.UnknownBranch(branch));
        };
    }

    private static net.vanfleteren.jazygit.git.files.Diffs diffs() {
        return new StubDiffs() {
            @Override
            public Either<GitError, DiffText> commitChanges(Sha sha, DiffOptions options) {
                return Either.right(DiffText.of(
                        "1 file changed, 1 insertion(+)\n\ndiff --git a/" + sha.value() + " b/" + sha.value() + "\n"));
            }

            @Override
            public Either<GitError, Diffs> uncommitted(List<FileEntry> files, DiffOptions options) {
                return Either.right(new Diffs(sample(files, FileEntry::staged), sample(files, FileEntry::unstaged)));
            }
        };
    }
}
