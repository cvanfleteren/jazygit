package net.vanfleteren.jazygit.model;

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
                new Branch("main", true),
                new Branch("feature/initial-layout", false),
                new Branch("feature/jgit-backend", false),
                new Branch("bugfix/focus-cycling", false)
        );
    }

    @Override
    public List<Commit> commits() {
        return List.of(
                new Commit("a1b2c3d", "Ada Lovelace", "2026-10-09", "Add initial TUI layout",
                        "+ Added JazygitApp entry point\n+ Added Files/Branches/Commits panels"),
                new Commit("b2c3d4e", "Ada Lovelace", "2026-10-08", "Scaffold Maven project",
                        "+ Added pom.xml\n+ Added TamboUI dependencies"),
                new Commit("c3d4e5f", "Grace Hopper", "2026-10-07", "Wire content panel",
                        "+ Added ContentPanel\n+ Added FileStatusView/BranchLogView/CommitDiffView"),
                new Commit("d4e5f6a", "Grace Hopper", "2026-10-06", "Implement focus cycling",
                        "+ Added FocusedPane enum\n+ Added Tab/Shift+Tab handlers"),
                new Commit("e5f6a7b", "Linus Torvalds", "2026-10-05", "Initial commit",
                        "+ Added empty repository skeleton"),
                new Commit("f6a7b8c", "Linus Torvalds", "2026-10-04", "Project setup",
                        "+ Added .gitignore\n+ Added LICENSE")
        );
    }
}
