package net.vanfleteren.jazygit.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.FileTree;
import net.vanfleteren.jazygit.git.model.FileTree.Dir;
import net.vanfleteren.jazygit.git.model.FileTree.File;
import org.junit.jupiter.api.Test;

import java.util.List;

class FileTreeTest {

    private static FileEntry modified(String path) {
        return new FileEntry(path, ChangeType.MODIFIED);
    }

    @Test
    void entriesCoverEveryFileBelowANode() {
        FileEntry a = modified("net/A.java");
        FileEntry b = modified("net/sub/B.java");
        FileEntry c = modified("c.txt");
        List<FileTree> roots = FileTree.of(List.of(a, b, c));

        // Directories sort before files.
        assertEquals(List.of(b, a), roots.get(0).entries());
        assertEquals(List.of(c), roots.get(1).entries());
    }

    @Test
    void emptyStatusHasNoNodes() {
        assertEquals(List.of(), FileTree.of(List.of()));
    }

    @Test
    void rootFilesAreSortedLeaves() {
        FileEntry b = modified("b.txt");
        FileEntry a = modified("a.txt");

        assertEquals(List.of(new File("a.txt", a), new File("b.txt", b)), FileTree.of(List.of(b, a)));
    }

    @Test
    void chainOfSingleDirectoriesIsMerged() {
        FileEntry app = modified("src/main/java/App.java");

        assertEquals(List.of(new Dir("src/main/java", "src/main/java", List.of(new File("App.java", app)))),
                FileTree.of(List.of(app)));
    }

    @Test
    void directoryWithFilesIsNotMergedIntoItsChild() {
        FileEntry a = modified("net/vanfleteren/A.java");
        FileEntry b = modified("net/vanfleteren/b/B.java");

        assertEquals(List.of(new Dir("net/vanfleteren", "net/vanfleteren", List.of(
                        new Dir("b", "net/vanfleteren/b", List.of(new File("B.java", b))),
                        new File("A.java", a)))),
                FileTree.of(List.of(a, b)));
    }

    @Test
    void directoryWithSeveralSubdirectoriesIsKeptButItsChildrenAreMerged() {
        FileEntry main = modified("src/main/java/App.java");
        FileEntry test = modified("src/test/java/AppTest.java");

        assertEquals(List.of(new Dir("src", "src", List.of(
                        new Dir("main/java", "src/main/java", List.of(new File("App.java", main))),
                        new Dir("test/java", "src/test/java", List.of(new File("AppTest.java", test)))))),
                FileTree.of(List.of(test, main)));
    }

    @Test
    void directoriesComeBeforeFiles() {
        FileEntry pom = modified("a.xml");
        FileEntry app = modified("z/App.java");

        assertEquals(List.of(new Dir("z", "z", List.of(new File("App.java", app))), new File("a.xml", pom)),
                FileTree.of(List.of(pom, app)));
    }
}
