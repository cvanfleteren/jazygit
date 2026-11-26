package net.vanfleteren.jazygit.core.git.model;

import static org.junit.jupiter.api.Assertions.*;

import net.vanfleteren.jazygit.core.repo.RepoPath;
import org.junit.jupiter.api.Test;

class PathGlobTest {

    @Test
    void aGlobWithoutASlashMatchesTheNameOfTheFileAnywhere() {
        PathGlob glob = PathGlob.of("go.sum");

        assertTrue(glob.matches(new RepoPath("go.sum")));
        assertTrue(glob.matches(new RepoPath("tools/go.sum")));
        assertFalse(glob.matches(new RepoPath("go.summary")));
    }

    @Test
    void aStarAloneMatchesEveryFile() {
        assertTrue(PathGlob.of("*").matches(new RepoPath("a/b/c.txt")));
        assertTrue(PathGlob.of("*").matches(new RepoPath("README")));
    }

    @Test
    void aGlobWithASlashMatchesThePathFromTheRoot() {
        PathGlob glob = PathGlob.of("src/*.java");

        assertTrue(glob.matches(new RepoPath("src/A.java")));
        assertFalse(glob.matches(new RepoPath("src/x/A.java")));
        assertFalse(glob.matches(new RepoPath("lib/src/A.java")));
    }

    @Test
    void twoStarsCrossSlashes() {
        PathGlob glob = PathGlob.of("src/**/*.java");

        assertTrue(glob.matches(new RepoPath("src/x/y/A.java")));
        assertFalse(glob.matches(new RepoPath("test/x/A.java")));
    }

    @Test
    void aLeadingDoubleStarAlsoMatchesAtTheRoot() {
        PathGlob glob = PathGlob.of("**/__snapshots__/*.snap");

        assertTrue(glob.matches(new RepoPath("__snapshots__/a.snap")));
        assertTrue(glob.matches(new RepoPath("web/src/__snapshots__/a.snap")));
        assertFalse(glob.matches(new RepoPath("web/snapshots/a.snap")));
    }

    @Test
    void aLeadingStarSlashNeedsADirectory() {
        PathGlob glob = PathGlob.of("*/package.json");

        assertTrue(glob.matches(new RepoPath("web/package.json")));
        assertFalse(glob.matches(new RepoPath("package.json")));
    }

    @Test
    void bracesAndClassesAreAlternatives() {
        assertTrue(PathGlob.of("*.{js,ts}").matches(new RepoPath("a/b.ts")));
        assertFalse(PathGlob.of("*.{js,ts}").matches(new RepoPath("a/b.tsx")));
        assertTrue(PathGlob.of("v[0-9].txt").matches(new RepoPath("v3.txt")));
        assertFalse(PathGlob.of("v[!0-9].txt").matches(new RepoPath("v3.txt")));
    }

    @Test
    void otherCharactersAreThemselves() {
        assertTrue(PathGlob.of("a+b(1).min.js").matches(new RepoPath("x/a+b(1).min.js")));
        assertFalse(PathGlob.of("a.js").matches(new RepoPath("abjs")));
    }
}
