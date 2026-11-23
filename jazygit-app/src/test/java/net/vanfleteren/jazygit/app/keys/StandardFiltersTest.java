package net.vanfleteren.jazygit.app.keys;

import static net.vanfleteren.jazygit.kernel.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.core.git.model.DiffFilter;
import net.vanfleteren.jazygit.core.git.model.DiffText;
import net.vanfleteren.jazygit.features.changes.diff.DiffFilterSettings;
import net.vanfleteren.jazygit.kernel.settings.DiffStyle;
import net.vanfleteren.jazygit.kernel.state.AreaId;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.diff.Filtering;
import net.vanfleteren.jazygit.kernel.state.diff.HunkFilters;
import net.vanfleteren.jazygit.kernel.state.diff.ScrolledContent;
import org.junit.jupiter.api.Test;

/**
 * The kernel hides hunks with the diff filters the diffs feature keeps, which the app gives it (ADR 184): those
 * enabled in the settings, and whether the part of the feature switched them off.
 */
class StandardFiltersTest {

    private static final AreaId AREA = new AreaId("unstaged");
    private static final ScrolledContent CONTENT = new ScrolledContent.Diff(DiffText.of("""
            diff --git a/A.java b/A.java
            --- a/A.java
            +++ b/A.java
            @@ -1,3 +1,3 @@
             package a;
            -import a.B;
            +import a.C;
            """));

    @Test
    void theKernelHidesWithTheFiltersEnabledInTheSettings() {
        Filtering filtering = enabled(loaded()).filtering(AREA, CONTENT);

        assertEquals(List.of("java-imports"), filtering.filters().map(DiffFilter::id));
        assertFalse(filtering.off());
        assertEquals(
                List.of("java-imports"),
                HunkFilters.of(enabled(loaded())).active().map(DiffFilter::id));
    }

    @Test
    void switchedOffInThePartTheFiltersAreCountedButHideNothing() {
        Model off = enabled(loaded()).withPart(DiffFilterSettings.OFF, true);

        Filtering filtering = off.filtering(AREA, CONTENT);

        assertEquals(List.of("java-imports"), filtering.filters().map(DiffFilter::id));
        assertTrue(filtering.off());
        // J and K step past a file only while the filters hide something.
        assertTrue(HunkFilters.of(off).active().isEmpty());
    }

    @Test
    void whatTheFiltersHideIsTheSameValueEachTimeItIsAskedFor() {
        Model model = enabled(loaded());

        assertNotEquals(Filtering.NONE, model.filtering(AREA, CONTENT));
        assertEquals(model.filtering(AREA, CONTENT), model.filtering(AREA, CONTENT));
    }

    private static Model enabled(Model model) {
        return model.mapPreferences(preferences -> preferences.withSettings(preferences
                .settings()
                .withDiffStyle(DiffStyle.RICH)
                .with(DiffFilterSettings.ENABLED, List.of("java-imports"))));
    }
}
