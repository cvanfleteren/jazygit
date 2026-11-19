package net.vanfleteren.jazygit.core.git.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PathGlobTest {

    @Test
    void aGlobWithoutASlashMatchesTheNameOfTheFileAnywhere() {
        PathGlob glob = PathGlob.of("go.sum");

        assertTrue(glob.matches("go.sum"));
        assertTrue(glob.matches("tools/go.sum"));
        assertFalse(glob.matches("go.summary"));
    }

    @Test
    void aStarAloneMatchesEveryFile() {
        assertTrue(PathGlob.of("*").matches("a/b/c.txt"));
        assertTrue(PathGlob.of("*").matches("README"));
    }

    @Test
    void aGlobWithASlashMatchesThePathFromTheRoot() {
        PathGlob glob = PathGlob.of("src/*.java");

        assertTrue(glob.matches("src/A.java"));
        assertFalse(glob.matches("src/x/A.java"));
        assertFalse(glob.matches("lib/src/A.java"));
    }

    @Test
    void twoStarsCrossSlashes() {
        PathGlob glob = PathGlob.of("src/**/*.java");

        assertTrue(glob.matches("src/x/y/A.java"));
        assertFalse(glob.matches("test/x/A.java"));
    }

    @Test
    void aLeadingDoubleStarAlsoMatchesAtTheRoot() {
        PathGlob glob = PathGlob.of("**/__snapshots__/*.snap");

        assertTrue(glob.matches("__snapshots__/a.snap"));
        assertTrue(glob.matches("web/src/__snapshots__/a.snap"));
        assertFalse(glob.matches("web/snapshots/a.snap"));
    }

    @Test
    void aLeadingStarSlashNeedsADirectory() {
        PathGlob glob = PathGlob.of("*/package.json");

        assertTrue(glob.matches("web/package.json"));
        assertFalse(glob.matches("package.json"));
    }

    @Test
    void bracesAndClassesAreAlternatives() {
        assertTrue(PathGlob.of("*.{js,ts}").matches("a/b.ts"));
        assertFalse(PathGlob.of("*.{js,ts}").matches("a/b.tsx"));
        assertTrue(PathGlob.of("v[0-9].txt").matches("v3.txt"));
        assertFalse(PathGlob.of("v[!0-9].txt").matches("v3.txt"));
    }

    @Test
    void otherCharactersAreThemselves() {
        assertTrue(PathGlob.of("a+b(1).min.js").matches("x/a+b(1).min.js"));
        assertFalse(PathGlob.of("a.js").matches("abjs"));
    }
}
