package net.vanfleteren.jazygit.model;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.git.model.RepoStatus.Head;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.GitError.DiffError;
import net.vanfleteren.jazygit.git.GitError.LogError;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.StubGitProvider;
import net.vanfleteren.jazygit.git.model.*;

import java.time.Instant;

/**
 * Fixed sample data used by tests, so they don't depend on the state of a real repository.
 */
public final class SampleData implements StubGitProvider {

    @Override
    public String repositoryName() {
        return "jazygit";
    }

    @Override
    public Either<ProcessError, RepoStatus> status() {
        return Either.right(new RepoStatus(Head.onBranch("main", new Sha("a1b2c3d4e5f60718293a4b5c6d7e8f9012345678")), List.of(
                new FileEntry("src/main/java/net/vanfleteren/jazygit/JazygitApp.java", ChangeType.MODIFIED),
                new FileEntry("src/main/java/net/vanfleteren/jazygit/ui/ContentPanel.java", ChangeType.ADDED),
                new FileEntry("src/main/java/net/vanfleteren/jazygit/model/SampleData.java", ChangeType.ADDED),
                new FileEntry("pom.xml", ChangeType.MODIFIED),
                new FileEntry("README.md", ChangeType.DELETED),
                new FileEntry("notes.txt", ChangeType.UNTRACKED)
        )));
    }

    @Override
    public Either<ProcessError, List<Branch>> branches() {
        return Either.right(List.of(
                new Branch("main", true, new Sha("a1b2c3d4e5f60718293a4b5c6d7e8f9012345678"), Instant.EPOCH, true, true),
                new Branch("feature/initial-layout", false, new Sha("0a1b2c3d4e5f60718293a4b5c6d7e8f901234567"), Instant.EPOCH, true),
                new Branch("feature/jgit-backend", false, new Sha("1a2b3c4d5e6f708192a3b4c5d6e7f80912345678")),
                new Branch("bugfix/focus-cycling", false, new Sha("c3d4e5f60718293a4b5c6d7e8f9012345678a1b2"))
        ));
    }

    @Override
    public Either<ProcessError, List<Commit>> commits() {
        return Either.right(MAIN);
    }

    @Override
    public Either<LogError, List<Commit>> log(String branch) {
        return switch (branch) {
            case "main" -> Either.right(MAIN);
            case "feature/initial-layout" -> Either.right(List.of(
                    commit("0a1b2c3", ADA, "2026-10-09T16:05:00Z", "Split the screen into panes", ""),
                    commit("9f8e7d6", ADA, "2026-10-09T15:40:00Z", "Add a placeholder status bar",
                            "Shows the repository name until the real status is loaded.")));
            case "feature/jgit-backend" -> Either.right(List.of(
                    commit("1a2b3c4", LINUS, "2026-10-08T21:15:00Z", "Read branches through JGit",
                            "Replaces the hard-coded branch list.\nThe current branch is marked.")));
            case "bugfix/focus-cycling" -> Either.right(MAIN.drop(2));
            default -> Either.left(new GitError.UnknownBranch(branch));
        };
    }

    private static final String ADA = "Ada Lovelace <ada@example.com>";
    private static final String GRACE = "Grace Hopper <grace@example.com>";
    private static final String LINUS = "Linus Torvalds <linus@example.com>";

    private static final List<Commit> MAIN = List.of(
            commit("a1b2c3d", ADA, "2026-10-09T12:30:00Z", "Add initial TUI layout",
                    "Adds the JazygitApp entry point and the Files/Branches/Commits panels."),
            commit("b2c3d4e", ADA, "2026-10-08T09:12:00Z", "Scaffold Maven project",
                    "Adds pom.xml with the TamboUI dependencies."),
            commit("c3d4e5f", GRACE, "2026-10-07T17:45:00Z", "Wire content panel", ""),
            commit("d4e5f6a", GRACE, "2026-10-06T11:00:00Z", "Implement focus cycling",
                    "Tab and Shift+Tab move the focus between the left panes."),
            commit("e5f6a7b", LINUS, "2026-10-05T08:20:00Z", "Initial commit", ""),
            commit("f6a7b8c", LINUS, "2026-10-04T19:05:00Z", "Project setup",
                    "Adds .gitignore and LICENSE.")
    );

    /**
     * @param author name and email, as {@code "Name <email>"}
     */
    private static Commit commit(String sha, String author, String time, String message, String body) {
        int lt = author.indexOf(" <");
        return new Commit(new Sha(sha), author.substring(0, lt), author.substring(lt + 2, author.length() - 1),
                Instant.parse(time), message, body);
    }

    @Override
    public Either<ProcessError, String> commitChanges(Sha sha) {
        return Either.right("1 file changed, 1 insertion(+)\n\ndiff --git a/" + sha + " b/" + sha + "\n");
    }

    @Override
    public Either<DiffError, Diffs> diff(List<FileEntry> files) {
        return Either.right(new Diffs(sample(files, FileEntry::staged), sample(files, FileEntry::unstaged)));
    }

    private static String sample(List<FileEntry> files, java.util.function.Predicate<FileEntry> filter) {
        return files
                .filter(filter)
                .map(f -> "diff --git a/" + f.path() + " b/" + f.path() + "\n@@ -1 +1 @@\n-old\n+new\n")
                .mkString();
    }
}
