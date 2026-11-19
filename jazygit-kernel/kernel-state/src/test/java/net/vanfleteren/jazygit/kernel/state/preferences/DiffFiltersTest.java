package net.vanfleteren.jazygit.kernel.state.preferences;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.DiffFilter;
import net.vanfleteren.jazygit.core.git.model.DiffFiltering;
import net.vanfleteren.jazygit.core.git.model.UnifiedDiff;
import net.vanfleteren.jazygit.core.git.model.UnifiedDiff.Hunk;
import net.vanfleteren.jazygit.kernel.settings.FilterDefinition;
import net.vanfleteren.jazygit.kernel.settings.FilterDefinitions;
import org.junit.jupiter.api.Test;

class DiffFiltersTest {

    @Test
    void everyBuiltInFilterCanBeUsed() {
        DiffFilters filters = DiffFilters.of(FilterDefinitions.NONE, List.empty(), false);

        assertTrue(filters.all().nonEmpty());
        assertEquals(
                List.empty(),
                filters.all()
                        .filter(choice -> choice.filter().isLeft())
                        .map(choice -> choice.definition().id()));
    }

    @Test
    void onlyTheEnabledOnesHideAndNoneWhenSwitchedOff() {
        DiffFilters filters = DiffFilters.of(FilterDefinitions.NONE, List.of("java-imports", "lockfiles"), false);

        assertEquals(List.of("java-imports", "lockfiles"), filters.active().map(DiffFilter::id));
        assertEquals(List.empty(), filters.withOff(true).active());
        assertEquals(
                List.of("java-imports", "lockfiles"),
                filters.withOff(true).enabledFilters().map(DiffFilter::id));
    }

    @Test
    void aFilterThatCannotBeUsedIsListedButHidesNothing() {
        FilterDefinitions user = new FilterDefinitions(List.of(
                new FilterDefinition("broken", Option.none(), List.of("cobol"), List.empty(), Option.none(), false)));

        DiffFilters filters = DiffFilters.of(user, List.of("broken"), false);

        assertTrue(filters.all().last().filter().isLeft());
        assertTrue(filters.all().last().enabled());
        assertEquals(List.empty(), filters.active());
    }

    @Test
    void theBuiltInImportFiltersMatchWhatTheyAreFor() {
        DiffFilters filters = DiffFilters.of(
                FilterDefinitions.NONE,
                List.of("java-imports", "python-imports", "javascript-imports", "go-imports", "rust-uses"),
                false);

        assertHidden(filters, "A.java", "-import java.util.List;", "+import module java.base;", "+import a.B.*;");
        assertHidden(filters, "a.py", "-import os", "+from a.b import c, d", "+from x import (y, z)");
        assertHidden(
                filters, "a.ts", "+import { a, b } from './a';", "+import './polyfill';", "-const fs = require('fs');");
        assertHidden(filters, "a.go", "+import \"fmt\"", "+import f \"fmt\"");
        assertHidden(filters, "a.rs", "+use std::io;", "-pub(crate) use a::{b, c};");
        assertEquals(Option.none(), DiffFiltering.hiding(hunk("+int x;"), filters.active()));
    }

    @Test
    void theLockfilesAndGeneratedFilesAreHiddenWhole() {
        DiffFilters filters = DiffFilters.of(FilterDefinitions.NONE, List.of("lockfiles", "generated"), false);

        assertHidden(filters, "web/package-lock.json", "+  \"a\": 1,");
        assertHidden(filters, "__snapshots__/a.snap", "+exports[`x`] = 1;");
        assertHidden(filters, "dist/app.min.js", "+var a=1;");
        assertEquals(List.empty(), DiffFiltering.applying(filters.active(), "src/app.js"));
    }

    private static void assertHidden(DiffFilters filters, String path, String... lines) {
        List<DiffFilter> applying = DiffFiltering.applying(filters.active(), path);
        assertTrue(DiffFiltering.hiding(hunk(lines), applying).isDefined(), path + " " + List.of(lines));
    }

    private static Hunk hunk(String... lines) {
        String text = "diff --git a/f b/f\n--- a/f\n+++ b/f\n@@ -1,9 +1,9 @@\n"
                + List.of(lines).map(line -> line + "\n").mkString();
        return UnifiedDiff.parse(text).files().head().hunks().head();
    }
}
