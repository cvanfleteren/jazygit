package net.vanfleteren.jazygit.git.model;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.DiffFilter.Problem;
import net.vanfleteren.jazygit.git.model.DiffFiltering.HunkStart;
import net.vanfleteren.jazygit.git.model.UnifiedDiff.Hunk;
import org.junit.jupiter.api.Test;

class DiffFilteringTest {

    private static final DiffFilter IMPORTS = DiffFilter.of(
                    "java-imports",
                    Option.of("Java imports"),
                    List.of("java"),
                    List.empty(),
                    Option.of(List.of("^import (static |module )?[A-Za-z0-9_.$]+(\\.\\*)?;$")))
            .get();
    private static final DiffFilter COPYRIGHT = DiffFilter.of(
                    "copyright",
                    Option.none(),
                    List.empty(),
                    List.of("*"),
                    Option.of(List.of("Copyright \\(c\\) \\d{4}")))
            .get();
    private static final DiffFilter LOCKFILES = DiffFilter.of(
                    "lockfiles", Option.none(), List.empty(), List.of("yarn.lock"), Option.none())
            .get();

    @Test
    void aHunkOfImportsOnlyIsHidden() {
        Hunk hunk = hunk(" package a;", "-import a.B;", "+import a.C;", "+import static a.D.e;", " ");

        assertEquals(Option.of(List.of(IMPORTS)), DiffFiltering.hiding(hunk, List.of(IMPORTS)));
    }

    @Test
    void aHunkWithAnyOtherChangeShowsWhole() {
        Hunk hunk = hunk("-import a.B;", "+import a.C;", " class X {", "-  int a;", "+  int b;");

        assertEquals(Option.none(), DiffFiltering.hiding(hunk, List.of(IMPORTS)));
    }

    @Test
    void blankLinesCountWhenAnotherLineMatched() {
        Hunk hunk = hunk("-import java.util.List;", "+import java.util.Map;", "+", "+import static a.B.c;");

        assertTrue(DiffFiltering.hiding(hunk, List.of(IMPORTS)).isDefined());
    }

    @Test
    void aHunkOfBlankLinesOnlyShows() {
        assertEquals(Option.none(), DiffFiltering.hiding(hunk(" a", "+", "+   "), List.of(IMPORTS)));
    }

    @Test
    void differentFiltersCoverOneHunkTogether() {
        Hunk hunk = hunk("- * Copyright (c) 2025 Someone", "+ * Copyright (c) 2026 Someone", "-import a.B;");

        assertEquals(Option.of(List.of(IMPORTS, COPYRIGHT)), DiffFiltering.hiding(hunk, List.of(IMPORTS, COPYRIGHT)));
    }

    @Test
    void aFilterWithoutPatternsHidesEveryHunk() {
        assertTrue(DiffFiltering.hiding(hunk("-a@1.0", "+a@1.1", "+"), List.of(LOCKFILES))
                .isDefined());
    }

    @Test
    void aHunkThatChangesNothingIsNotHidden() {
        assertEquals(Option.none(), DiffFiltering.hiding(hunk(" a", " b"), List.of(LOCKFILES)));
    }

    @Test
    void noFilterHidesNothing() {
        assertEquals(Option.none(), DiffFiltering.hiding(hunk("-import a.B;"), List.empty()));
    }

    @Test
    void aFilterAppliesByLanguageOrByPath() {
        List<DiffFilter> all = List.of(IMPORTS, COPYRIGHT, LOCKFILES);

        assertEquals(List.of(IMPORTS, COPYRIGHT), DiffFiltering.applying(all, "src/A.java"));
        assertEquals(List.of(COPYRIGHT, LOCKFILES), DiffFiltering.applying(all, "web/yarn.lock"));
        assertEquals(List.of(COPYRIGHT), DiffFiltering.applying(all, "a.kt"));
    }

    @Test
    void aHunkStartsWhereItsHeaderSaysInBothFiles() {
        assertEquals(
                Option.of(new HunkStart(12, 11)),
                DiffFiltering.start(new Hunk("@@ -12,3 +11,0 @@ class A", List.empty())));
        assertEquals(Option.of(new HunkStart(1, 1)), DiffFiltering.start(new Hunk("@@ -1 +1 @@", List.empty())));
        assertEquals(Option.none(), DiffFiltering.start(new Hunk("", List.empty())));
    }

    @Test
    void aFilterAsWrittenCanBeWrong() {
        assertEquals(
                new Problem.NoScope(),
                DiffFilter.of("x", Option.none(), List.empty(), List.empty(), Option.none())
                        .getLeft());
        assertEquals(
                new Problem.UnknownLanguage("cobol"),
                DiffFilter.of("x", Option.none(), List.of("cobol"), List.empty(), Option.none())
                        .getLeft());
        assertInstanceOf(
                Problem.BadPattern.class,
                DiffFilter.of("x", Option.none(), List.of("java"), List.empty(), Option.of(List.of("(")))
                        .getLeft());
    }

    @Test
    void aFilterIsNamedByItsIdWithoutAName() {
        assertEquals("copyright", COPYRIGHT.name());
    }

    /**
     * A hunk of {@code lines}, each with its marker.
     */
    private static Hunk hunk(String... lines) {
        String text = "diff --git a/A.java b/A.java\n--- a/A.java\n+++ b/A.java\n@@ -1,9 +1,9 @@\n"
                + List.of(lines).map(line -> line + "\n").mkString();
        return UnifiedDiff.parse(text).files().head().hunks().head();
    }
}
