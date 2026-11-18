package net.vanfleteren.jazygit.changes.diff;

import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.git.model.DiffFiltering.HunkStart;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.HiddenHunks.Ranges;
import net.vanfleteren.jazygit.settings.FilterDefinition;
import net.vanfleteren.jazygit.settings.FilterDefinitions;
import net.vanfleteren.jazygit.state.AreaId;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.diff.Anchor;
import net.vanfleteren.jazygit.state.diff.DiffCursorAt;
import net.vanfleteren.jazygit.state.diff.Opened;
import net.vanfleteren.jazygit.state.diff.Scrolled;
import net.vanfleteren.jazygit.state.diff.ScrolledContent;
import net.vanfleteren.jazygit.state.preferences.SaveSettings;
import net.vanfleteren.jazygit.ui.keys.Keymap;
import net.vanfleteren.jazygit.ui.keys.Scopes;
import net.vanfleteren.jazygit.ui.widgets.PickDialog;
import org.junit.jupiter.api.Test;

class DiffFiltersUpdateTest {

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
    void ctrlFOpensThePopupOfTheFilters() {
        Next next = Update.update(loaded(), new DiffFiltersMsg.Requested());

        DiffFiltersPopup popup = next.model().popup(DiffFiltersPopup.class).get();
        assertTrue(
                popup.filters().all().exists(choice -> choice.definition().id().equals("java-imports")));
        assertEquals(List.empty(), next.cmds());
    }

    @Test
    void togglingAFilterEnablesItSavesItAndKeepsThePopupOpen() {
        Model open = Update.update(loaded(), new DiffFiltersMsg.Requested()).model();

        Next next = Update.update(open, new DiffFiltersMsg.Toggled("java-imports"));

        assertEquals(
                List.of("java-imports"), next.model().preferences().settings().diffFilters());
        assertEquals(
                List.of(new SaveSettings(
                        open.preferences().settings(),
                        next.model().preferences().settings())),
                next.cmds());
        assertTrue(next.model()
                .popup(DiffFiltersPopup.class)
                .get()
                .filters()
                .enabledFilters()
                .exists(filter -> filter.id().equals("java-imports")));

        Next again = Update.update(next.model(), new DiffFiltersMsg.Toggled("java-imports"));
        assertEquals(List.empty(), again.model().preferences().settings().diffFilters());
    }

    @Test
    void aFilterThatCannotBeUsedIsNotEnabled() {
        Model model = loaded().mapPreferences(preferences -> preferences.withSettings(preferences
                .settings()
                .withFilterDefinitions(new FilterDefinitions(List.of(new FilterDefinition(
                        "broken", Option.none(), List.of("cobol"), List.empty(), Option.none(), false))))));

        Next next = Update.update(model, new DiffFiltersMsg.Toggled("broken"));

        assertSame(model, next.model());
        assertEquals(List.empty(), next.cmds());
    }

    @Test
    void theSwitchTurnsAllOffForTheSessionWithoutSaving() {
        Model model = enabled(loaded());

        Next off = Update.update(model, new DiffFiltersMsg.AllToggled());

        assertTrue(off.model().preferences().diffFilters().active().isEmpty());
        assertEquals(
                List.of("java-imports"), off.model().preferences().settings().diffFilters());
        assertEquals(List.empty(), off.cmds());
        assertFalse(Update.update(off.model(), new DiffFiltersMsg.AllToggled())
                .model()
                .preferences()
                .diffFilters()
                .active()
                .isEmpty());
    }

    @Test
    void theDialogListsTheSwitchFirstThenTheFiltersByLanguageThenByPath() {
        List<PickDialog.Row<DiffFiltersMsg>> rows =
                DiffFiltersDialog.rows(enabled(loaded()).preferences().diffFilters());

        assertEquals(new DiffFiltersMsg.AllToggled(), rows.head().value());
        assertEquals(new DiffFiltersMsg.Toggled("java-imports"), rows.get(1).value());
        assertEquals("[x]", rows.get(1).cells().head().text());
        assertEquals("[ ]", rows.get(2).cells().head().text());
        assertTrue(rows.indexWhere(row -> row.value().equals(new DiffFiltersMsg.Toggled("java-imports")))
                < rows.indexWhere(row -> row.value().equals(new DiffFiltersMsg.Toggled("lockfiles"))));
    }

    @Test
    void enterOnAMarkerOpensItsHunksAndOnTheMarkerKeptAboveThemHidesThemAgain() {
        Model model = enabled(loaded());
        List<Ranges> hunks = List.of(new Ranges(1, 3, 1, 3));

        Model opened = Update.update(model, new FilteredMsg.Opened(at(new Anchor.Filtered("A.java", hunks, false))))
                .model();

        assertEquals(
                Option.of(HashSet.of(new Opened.Hunk("A.java", new HunkStart(1, 1)))),
                opened.repoView().opened(AREA, CONTENT).map(Opened::hunks));

        Model closed = Update.update(opened, new FilteredMsg.Closed(at(new Anchor.Filtered("A.java", hunks, true))))
                .model();

        assertEquals(
                Option.of(HashSet.empty()),
                closed.repoView().opened(AREA, CONTENT).map(Opened::hunks));
    }

    @Test
    void enterOnALineIsNoKeyOfTheFilters() {
        DiffCursorAt line = at(new Anchor.OnLine("A.java", ReviewThread.Side.RIGHT, 2));

        assertEquals(
                Option.none(),
                ExpandAction.ACTION
                        .keys()
                        .apply(Keymap.empty())
                        .handle(Scopes.DIFF, KeyEvent.ofKey(KeyCode.ENTER), Option.of(line))
                        .filter(FilteredMsg.class::isInstance));
    }

    @Test
    void whatTheFiltersHideIsTheSameValueEachTimeItIsAskedFor() {
        Model model = enabled(loaded());

        assertEquals(model.filtering(AREA, CONTENT), model.filtering(AREA, CONTENT));
    }

    private static Model enabled(Model model) {
        return model.mapPreferences(preferences ->
                preferences.withSettings(preferences.settings().withDiffFilters(List.of("java-imports"))));
    }

    private static DiffCursorAt at(Anchor cursor) {
        return new DiffCursorAt(
                AREA,
                new Scrolled(CONTENT, 0, 0, Option.none(), Option.none()),
                cursor,
                Option.none(),
                Option.none(),
                Option.none());
    }
}
