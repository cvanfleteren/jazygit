package net.vanfleteren.jazygit.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.FileTree;
import net.vanfleteren.jazygit.git.model.FileTree.Dir;
import net.vanfleteren.jazygit.git.model.FileTree.File;
import net.vanfleteren.jazygit.git.model.Fixtures;
import org.junit.jupiter.api.Test;

class FileTreeTest {

    private static FileEntry modified(String path) {
        return Fixtures.fileEntry(path, ChangeType.MODIFIED);
    }

    @Test
    void entriesCoverEveryFileBelowANode() {
        FileEntry a = modified("net/A.java");
        FileEntry b = modified("net/sub/B.java");
        FileEntry c = modified("c.txt");
        List<FileTree<FileEntry>> roots = FileTree.of(List.of(a, b, c));

        // Files sort before directories.
        assertEquals(List.of(c), roots.get(0).entries());
        assertEquals(List.of(a, b), roots.get(1).entries());
    }

    @Test
    void directEntriesSkipSubdirectories() {
        FileEntry a = modified("net/A.java");
        FileEntry b = modified("net/sub/B.java");
        FileEntry c = modified("c.txt");
        Dir<FileEntry> root = new Dir<>("root", "", FileTree.of(List.of(a, b, c)));

        assertEquals(List.of(c), root.directEntries());
        assertEquals(List.of(a), root.children().get(1).directEntries());
        assertEquals(List.of(c), new File<>("c.txt", c).directEntries());
    }

    @Test
    void emptyStatusHasNoNodes() {
        assertEquals(List.of(), FileTree.of(List.of()));
    }

    @Test
    void rootFilesAreSortedLeaves() {
        FileEntry b = modified("b.txt");
        FileEntry a = modified("a.txt");

        assertEquals(List.of(new File<>("a.txt", a), new File<>("b.txt", b)), FileTree.of(List.of(b, a)));
    }

    @Test
    void chainOfSingleDirectoriesIsMerged() {
        FileEntry app = modified("src/main/java/App.java");

        assertEquals(
                List.of(new Dir<>("src/main/java", "src/main/java", List.of(new File<>("App.java", app)))),
                FileTree.of(List.of(app)));
    }

    @Test
    void directoryWithFilesIsNotMergedIntoItsChild() {
        FileEntry a = modified("net/vanfleteren/A.java");
        FileEntry b = modified("net/vanfleteren/b/B.java");

        assertEquals(
                List.of(new Dir<>(
                        "net/vanfleteren",
                        "net/vanfleteren",
                        List.of(
                                new File<>("A.java", a),
                                new Dir<>("b", "net/vanfleteren/b", List.of(new File<>("B.java", b)))))),
                FileTree.of(List.of(a, b)));
    }

    @Test
    void directoryWithSeveralSubdirectoriesIsKeptButItsChildrenAreMerged() {
        FileEntry main = modified("src/main/java/App.java");
        FileEntry test = modified("src/test/java/AppTest.java");

        assertEquals(
                List.of(new Dir<>(
                        "src",
                        "src",
                        List.of(
                                new Dir<>("main/java", "src/main/java", List.of(new File<>("App.java", main))),
                                new Dir<>("test/java", "src/test/java", List.of(new File<>("AppTest.java", test)))))),
                FileTree.of(List.of(test, main)));
    }

    @Test
    void filesComeBeforeDirectories() {
        FileEntry pom = modified("z.xml");
        FileEntry app = modified("a/App.java");

        assertEquals(
                List.of(new File<>("z.xml", pom), new Dir<>("a", "a", List.of(new File<>("App.java", app)))),
                FileTree.of(List.of(pom, app)));
    }
}
