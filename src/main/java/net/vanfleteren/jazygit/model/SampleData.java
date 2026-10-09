package net.vanfleteren.jazygit.model;

import java.time.Instant;
import java.util.List;

/**
 * Fixed sample data used by tests, so they don't depend on the state of a real repository.
 */
public final class SampleData implements GitInfoProvider {

    @Override
    public String repositoryName() {
        return "jazygit";
    }

    @Override
    public RepoStatus status() {
        return new RepoStatus("main", "a1b2c3d4e5f60718293a4b5c6d7e8f9012345678", List.of(
                new FileEntry("src/main/java/net/vanfleteren/jazygit/JazygitApp.java", ChangeType.MODIFIED),
                new FileEntry("src/main/java/net/vanfleteren/jazygit/ui/ContentPanel.java", ChangeType.ADDED),
                new FileEntry("src/main/java/net/vanfleteren/jazygit/model/SampleData.java", ChangeType.ADDED),
                new FileEntry("pom.xml", ChangeType.MODIFIED),
                new FileEntry("README.md", ChangeType.DELETED),
                new FileEntry("notes.txt", ChangeType.UNTRACKED)
        ));
    }

    @Override
    public List<Branch> branches() {
        return List.of(
                new Branch("main", true, "a1b2c3d4e5f60718293a4b5c6d7e8f9012345678"),
                new Branch("feature/initial-layout", false, "0a1b2c3d4e5f60718293a4b5c6d7e8f901234567"),
                new Branch("feature/jgit-backend", false, "1a2b3c4d5e6f708192a3b4c5d6e7f80912345678"),
                new Branch("bugfix/focus-cycling", false, "c3d4e5f60718293a4b5c6d7e8f9012345678a1b2")
        );
    }

    @Override
    public List<Commit> commits() {
        return log("main");
    }

    @Override
    public List<Commit> log(String branch) {
        return switch (branch) {
            case "main" -> MAIN;
            case "feature/initial-layout" -> List.of(
                    commit("0a1b2c3", ADA, "2026-10-09T16:05:00Z", "Split the screen into panes", ""),
                    commit("9f8e7d6", ADA, "2026-10-09T15:40:00Z", "Add a placeholder status bar",
                            "Shows the repository name until the real status is loaded."));
            case "feature/jgit-backend" -> List.of(
                    commit("1a2b3c4", LINUS, "2026-10-08T21:15:00Z", "Read branches through JGit",
                            "Replaces the hard-coded branch list.\nThe current branch is marked."));
            case "bugfix/focus-cycling" -> MAIN.subList(2, MAIN.size());
            default -> throw new IllegalStateException("Unknown branch: " + branch);
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
        return new Commit(sha, author.substring(0, lt), author.substring(lt + 2, author.length() - 1),
                Instant.parse(time), message, body);
    }

    /**
     * Does nothing: the sample data never changes.
     */
    @Override
    public void checkout(String branch) {
    }

    @Override
    public void stage(List<String> paths) {
    }

    @Override
    public void unstageNew(List<String> paths) {
    }

    @Override
    public void unstage(List<String> paths) {
    }

    @Override
    public Diffs diff(List<FileEntry> files) {
        return new Diffs(sample(files, FileEntry::staged), sample(files, FileEntry::unstaged));
    }

    private static String sample(List<FileEntry> files, java.util.function.Predicate<FileEntry> filter) {
        return files.stream()
                .filter(filter)
                .map(f -> "diff --git a/" + f.path() + " b/" + f.path() + "\n@@ -1 +1 @@\n-old\n+new\n")
                .collect(java.util.stream.Collectors.joining());
    }
}
