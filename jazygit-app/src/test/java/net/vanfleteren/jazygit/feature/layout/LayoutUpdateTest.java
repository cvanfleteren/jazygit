package net.vanfleteren.jazygit.feature.layout;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.feature.help.HelpMsg;
import net.vanfleteren.jazygit.feature.theme.ThemePopup;
import net.vanfleteren.jazygit.feature.theme.ThemeScope;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.Stash;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.settings.Settings;
import net.vanfleteren.jazygit.settings.ThemeName;
import net.vanfleteren.jazygit.state.LoadMsg;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.RepositoryTheme;
import net.vanfleteren.jazygit.state.SaveSettings;
import net.vanfleteren.jazygit.state.Screen;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.ViewMsg;
import org.junit.jupiter.api.Test;

class LayoutUpdateTest {

    private final Model model = TestModels.loaded();

    @Test
    void aStepWidensTheLeftColumnOfTheScreenShownAndKeepsIt() {
        Next next = Update.update(model, new LayoutMsg.Stepped(5));

        Settings wider = TestModels.PLAIN.settings().withRepositoryWidth(35);
        assertEquals(wider, next.model().preferences().settings());
        assertEquals(35, next.model().preferences().leftWidth(Screen.REPOSITORY));
        assertEquals(30, next.model().preferences().leftWidth(Screen.PULL_REQUESTS));
        assertEquals(List.of(new SaveSettings(TestModels.PLAIN.settings(), wider)), next.cmds());
    }

    @Test
    void eachScreenHasItsOwnWidth() {
        Next next = Update.update(model.withScreen(Screen.PULL_REQUESTS), new LayoutMsg.Stepped(-5));

        assertEquals(
                TestModels.PLAIN.settings().withPullRequestsWidth(25),
                next.model().preferences().settings());
    }

    @Test
    void theWidthStaysWithinItsRangeAndAStepPastItKeepsNothing() {
        Model narrowest = model.mapPreferences(preferences ->
                preferences.withSettings(TestModels.PLAIN.settings().withRepositoryWidth(Settings.MIN_WIDTH)));

        Next next = Update.update(narrowest, new LayoutMsg.Stepped(-5));

        assertEquals(narrowest, next.model());
        assertEquals(List.of(), next.cmds());
    }

    @Test
    void theKeysDoNothingWhileAPopupIsOpen() {
        Model open = model.openPopup(new ThemePopup(
                ThemeName.DARK, Option.none(), ThemeScope.EVERYWHERE, RepositoryTheme.NONE, ThemeName.DARK));

        assertEquals(Next.of(open), Update.update(open, new LayoutMsg.Stepped(5)));
        assertEquals(Next.of(open), Update.update(open, new LayoutMsg.Resized(Screen.REPOSITORY, 50)));
    }

    @Test
    void aDraggedWidthShowsAndIsOnlyKeptWhenLetGo() {
        Next dragged = Update.update(model, new LayoutMsg.Resized(Screen.REPOSITORY, 90));

        assertEquals(Settings.MAX_WIDTH, dragged.model().preferences().leftWidth(Screen.REPOSITORY));
        assertEquals(TestModels.PLAIN.settings(), dragged.model().preferences().settings());
        assertEquals(List.of(), dragged.cmds());

        Next settled = Update.update(dragged.model(), new LayoutMsg.Settled(Screen.REPOSITORY));

        Settings kept = TestModels.PLAIN.settings().withRepositoryWidth(Settings.MAX_WIDTH);
        assertEquals(kept, settled.model().preferences().settings());
        assertEquals(Option.none(), settled.model().preferences().dragged());
        assertEquals(List.of(new SaveSettings(TestModels.PLAIN.settings(), kept)), settled.cmds());
    }

    @Test
    void lettingGoWhereItStartedKeepsNothing() {
        Model dragged = Update.update(model, new LayoutMsg.Resized(Screen.REPOSITORY, 30))
                .model();

        Next settled = Update.update(dragged, new LayoutMsg.Settled(Screen.REPOSITORY));

        assertEquals(model, settled.model());
        assertEquals(List.of(), settled.cmds());
    }

    @Test
    void aStepEndsADragAndALateReleaseKeepsTheStep() {
        Model dragged = Update.update(model, new LayoutMsg.Resized(Screen.REPOSITORY, 50))
                .model();

        Next stepped = Update.update(dragged, new LayoutMsg.Stepped(5));

        assertEquals(55, stepped.model().preferences().leftWidth(Screen.REPOSITORY));
        assertEquals(Option.none(), stepped.model().preferences().dragged());
        Next released = Update.update(stepped.model(), new LayoutMsg.Settled(Screen.REPOSITORY));
        assertEquals(Next.of(stepped.model()), released);
    }

    @Test
    void aDraggedPaneShowsWithThePaneUnderItAndIsOnlyKeptWhenLetGo() {
        Next dragged = Update.update(model, new LayoutMsg.PaneResized(Screen.REPOSITORY, 0, 35));

        assertEquals(List.of(35, 10, 30), dragged.model().preferences().leftHeights(Screen.REPOSITORY));
        assertEquals(TestModels.PLAIN.settings(), dragged.model().preferences().settings());
        assertEquals(List.of(), dragged.cmds());

        Next settled = Update.update(dragged.model(), new LayoutMsg.PaneSettled(Screen.REPOSITORY));

        Settings kept = TestModels.PLAIN.settings().withRepositoryHeights(List.of(35, 10, 30));
        assertEquals(kept, settled.model().preferences().settings());
        assertEquals(Option.none(), settled.model().preferences().draggedHeights());
        assertEquals(List.of(new SaveSettings(TestModels.PLAIN.settings(), kept)), settled.cmds());
    }

    @Test
    void eachPaneAndThePaneUnderItKeepTheirLeastHeight() {
        // The branches start 25 percent below the top of the files.
        Next taller = Update.update(model, new LayoutMsg.PaneResized(Screen.REPOSITORY, 1, 90));
        Next shorter = Update.update(model, new LayoutMsg.PaneResized(Screen.REPOSITORY, 1, 20));
        // The stash under the commits has no height of its own: it takes what the others leave.
        Next commits = Update.update(model, new LayoutMsg.PaneResized(Screen.REPOSITORY, 2, 90));

        assertEquals(List.of(25, 40, 10), taller.model().preferences().leftHeights(Screen.REPOSITORY));
        assertEquals(List.of(25, 10, 40), shorter.model().preferences().leftHeights(Screen.REPOSITORY));
        assertEquals(List.of(25, 20, 45), commits.model().preferences().leftHeights(Screen.REPOSITORY));
    }

    @Test
    void eachScreenHasItsOwnHeights() {
        Model dragged = Update.update(model, new LayoutMsg.PaneResized(Screen.PULL_REQUESTS, 1, 82))
                .model();

        assertEquals(List.of(32, 50), dragged.preferences().leftHeights(Screen.PULL_REQUESTS));
        assertEquals(Settings.DEFAULT_REPOSITORY_HEIGHTS, dragged.preferences().leftHeights(Screen.REPOSITORY));
        assertEquals(Next.of(dragged), Update.update(dragged, new LayoutMsg.PaneSettled(Screen.REPOSITORY)));
    }

    @Test
    void aPaneIsNotDraggedWhileAPopupIsOpen() {
        Model open = model.openPopup(new ThemePopup(
                ThemeName.DARK, Option.none(), ThemeScope.EVERYWHERE, RepositoryTheme.NONE, ThemeName.DARK));

        assertEquals(Next.of(open), Update.update(open, new LayoutMsg.PaneResized(Screen.REPOSITORY, 0, 50)));
    }

    @Test
    void lettingGoOfAnotherScreenOrOfNoDragDoesNothing() {
        Model dragged = Update.update(model, new LayoutMsg.Resized(Screen.REPOSITORY, 50))
                .model();

        assertEquals(Next.of(dragged), Update.update(dragged, new LayoutMsg.Settled(Screen.PULL_REQUESTS)));
        assertEquals(Next.of(model), Update.update(model, new LayoutMsg.Settled(Screen.REPOSITORY)));
    }

    private static Model focused(Model model, String id) {
        return Update.update(model, new ViewMsg.Focused(id, false)).model();
    }

    private static Model content(Model model) {
        return Update.update(model, new ViewMsg.Focused("diff-unstaged", true)).model();
    }

    private static Model measured(Model model, int rows) {
        return Update.update(model, new LayoutMsg.Measured(rows)).model();
    }

    private static Model toggled(Model model) {
        return Update.update(model, new LayoutMsg.MaximizeToggled()).model();
    }

    private static List<Integer> heights(Next next) {
        return next.model().preferences().settings().repositoryHeights();
    }

    @Test
    void plusMakesTheFocusedPaneTallerFromThePaneUnderItAndKeepsIt() {
        Next next = Update.update(focused(model, Scopes.BRANCHES.id()), new LayoutMsg.PaneStepped(5));

        Settings taller = TestModels.PLAIN.settings().withRepositoryHeights(List.of(25, 25, 25));
        assertEquals(taller, next.model().preferences().settings());
        assertEquals(List.of(new SaveSettings(TestModels.PLAIN.settings(), taller)), next.cmds());
    }

    @Test
    void minusMakesTheFocusedPaneShorter() {
        Next next = Update.update(focused(model, Scopes.FILES.id()), new LayoutMsg.PaneStepped(-5));

        assertEquals(List.of(20, 25, 30), heights(next));
    }

    @Test
    void theLastPaneMovesTheBorderAboveIt() {
        Next next = Update.update(focused(model, Scopes.STASH.id()), new LayoutMsg.PaneStepped(5));

        assertEquals(List.of(25, 20, 25), heights(next));
    }

    @Test
    void plusTakesFromThePanesUnderThePaneFirstAndThenFromThePanesAboveIt() {
        Model branches = focused(model, Scopes.BRANCHES.id());

        // The commits give 20 percent, the stash 15, the files 15: each keeps its least height.
        assertEquals(List.of(25, 40, 10), heights(stepped(branches, 4)));
        assertEquals(List.of(25, 55, 10), heights(stepped(branches, 7)));
        assertEquals(List.of(10, 70, 10), heights(stepped(branches, 10)));
        assertEquals(List.of(10, 70, 10), heights(stepped(branches, 11)));
    }

    @Test
    void plusMaximizesThePaneWhenTheOthersAreAsShortAsTheyGet() {
        // In 100 rows, the others are 10 rows each at their least height, 3 maximized.
        Model branches = measured(focused(model, Scopes.BRANCHES.id()), 100);
        Model tallest = stepped(branches, 10).model();
        assertEquals(List.of(10, 70, 10), tallest.preferences().leftHeights(Screen.REPOSITORY));
        assertEquals(Option.none(), tallest.preferences().maximizedPane(Screen.REPOSITORY));

        Next maximizing = Update.update(tallest, new LayoutMsg.PaneStepped(5));
        Model maximized = maximizing.model();

        assertEquals(Option.of(1), maximized.preferences().maximizedPane(Screen.REPOSITORY));
        // The heights from before the press are kept, and were saved by it.
        assertEquals(List.of(10, 70, 10), heights(maximizing));
        assertEquals(List.empty(), maximizing.cmds());
        assertEquals(Next.of(maximized), Update.update(maximized, new LayoutMsg.PaneStepped(5)));
        Next shorter = Update.update(maximized, new LayoutMsg.PaneStepped(-5));
        assertEquals(Option.none(), shorter.model().preferences().maximizedPane(Screen.REPOSITORY));
        assertEquals(List.of(10, 65, 15), heights(shorter));
    }

    @Test
    void plusDoesNotMaximizeAColumnTooShortForItToShowTaller() {
        // In 20 rows, the least height is 2 rows, under the 3 a pane keeps while another is maximized.
        Model tallest =
                stepped(measured(focused(model, Scopes.BRANCHES.id()), 20), 15).model();

        assertEquals(Next.of(tallest), Update.update(tallest, new LayoutMsg.PaneStepped(5)));
    }

    @Test
    void plusMaximizesThePaneWhenTheLastRowItCanTakeLandsInAPaneBetween() {
        // In 53 rows a step is not a whole row: the last percent the files give moves the border between the files
        // and the branches, but not the one between the branches and the commits.
        Next next = stepped(measured(focused(model, Scopes.COMMITS.id()), 53), 20);

        assertEquals(Option.of(2), next.model().preferences().maximizedPane(Screen.REPOSITORY));
    }

    @Test
    void plusOnTheLastPaneTakesFromThePanesAboveItAndThenMaximizesIt() {
        // 100 stashes need more rows than the stash gets in a column of 100 rows.
        Model stash = measured(focused(withStashes(100), Scopes.STASH.id()), 100);

        Next maximized = stepped(stash, 20);

        assertEquals(Option.of(3), maximized.model().preferences().maximizedPane(Screen.REPOSITORY));
        assertEquals(List.of(10, 10, 10), heights(maximized));
    }

    @Test
    void plusWhileAnotherPaneIsMaximizedEndsItAndStepsFromTheHeightsShown() {
        // In 40 rows, the maximized files are shown 65 percent tall raised to the least heights, (65, 10, 10): the
        // commits under the branches give nothing, so the stash gives 5.
        Model maximized = toggled(measured(focused(model, Scopes.FILES.id()), 40));

        Next next = Update.update(focused(maximized, Scopes.BRANCHES.id()), new LayoutMsg.PaneStepped(5));

        assertEquals(Option.none(), next.model().preferences().maximizedPane(Screen.REPOSITORY));
        assertEquals(List.of(65, 15, 10), heights(next));
    }

    @Test
    void plusOnAMaximizedPaneInAColumnTooShortToShowItSoEndsTheMaximizedPane() {
        Model maximized = toggled(measured(focused(model, Scopes.FILES.id()), 10));

        Next next = Update.update(maximized, new LayoutMsg.PaneStepped(5));

        assertEquals(Option.none(), next.model().preferences().maximizedPane(Screen.REPOSITORY));
    }

    private static Next stepped(Model model, int times) {
        return List.range(1, times)
                .foldLeft(
                        Update.update(model, new LayoutMsg.PaneStepped(5)),
                        (next, time) -> Update.update(next.model(), new LayoutMsg.PaneStepped(5)));
    }

    @Test
    void aStepIsBigEnoughToShowThePaneARowTallerOrShorter() {
        // In a column of 10 rows, 5 percent is half a row: the branches take rows 3 to 4 until their bottom border is
        // 55 percent below the top of the files, at 5.5 rows.
        Next next = Update.update(measured(focused(model, Scopes.BRANCHES.id()), 10), new LayoutMsg.PaneStepped(5));

        assertEquals(List.of(25, 30, 20), heights(next));
    }

    @Test
    void aPaneStopsAtItsLeastHeight() {
        Model files = focused(model, Scopes.FILES.id());
        Model dragged = Update.update(files, new LayoutMsg.PaneResized(Screen.REPOSITORY, 0, 12))
                .model();
        Model kept = Update.update(dragged, new LayoutMsg.PaneSettled(Screen.REPOSITORY))
                .model();

        Next least = Update.update(kept, new LayoutMsg.PaneStepped(-5));

        assertEquals(List.of(10, 35, 30), heights(least));
        assertEquals(Next.of(least.model()), Update.update(least.model(), new LayoutMsg.PaneStepped(-5)));
    }

    @Test
    void aStashThatShowsAllItsStashesIsNotMadeTaller() {
        // The stash has no stashes: a blank line in a border, of the 22 rows its share has in a column of 100 rows.
        Model stash = measured(focused(model, Scopes.STASH.id()), 100);

        assertEquals(Next.of(stash), Update.update(stash, new LayoutMsg.PaneStepped(5)));
    }

    @Test
    void theCommitsMadeShorterGiveRowsToAStashThatIsCutOff() {
        // 30 stashes need 32 rows, of the 22 the stash has in a column of 100 rows.
        Model commits = measured(focused(withStashes(30), Scopes.COMMITS.id()), 100);

        assertEquals(List.of(25, 20, 25), heights(Update.update(commits, new LayoutMsg.PaneStepped(-5))));
    }

    @Test
    void theCommitsAreNotMadeShorterForAStashThatShowsAllItsStashes() {
        Model commits = measured(focused(model, Scopes.COMMITS.id()), 100);

        assertEquals(Next.of(commits), Update.update(commits, new LayoutMsg.PaneStepped(-5)));
    }

    @Test
    void theStashIsMadeShorterFromTheRowsItShows() {
        // 10 stashes show in 12 of the 22 rows of the share of the stash in a column of 100 rows: its share is first
        // 15 percent, which leaves the commits 40, and a step leaves it 10.
        Model stash = measured(focused(withStashes(10), Scopes.STASH.id()), 100);

        assertEquals(List.of(25, 20, 45), heights(Update.update(stash, new LayoutMsg.PaneStepped(-5))));
    }

    private static Model withStashes(int count) {
        List<Stash> stashes = List.range(0, count)
                .map(i -> new Stash("stash@{" + i + "}", new Sha("s" + i), "wip " + i, Option.none(), Instant.EPOCH));
        return Update.update(TestModels.loaded(), new LoadMsg.StashesLoaded(stashes))
                .model();
    }

    @Test
    void theKeysDoNothingOnTheContentPanelOrBehindAPopup() {
        Model content = content(model);
        Model open = focused(model, Scopes.FILES.id())
                .openPopup(new ThemePopup(
                        ThemeName.DARK, Option.none(), ThemeScope.EVERYWHERE, RepositoryTheme.NONE, ThemeName.DARK));

        assertEquals(Next.of(content), Update.update(content, new LayoutMsg.PaneStepped(5)));
        assertEquals(Next.of(model), Update.update(model, new LayoutMsg.PaneStepped(5)));
        assertEquals(Next.of(open), Update.update(open, new LayoutMsg.PaneStepped(5)));
        assertEquals(Next.of(open), Update.update(open, new LayoutMsg.MaximizeToggled()));
    }

    @Test
    void theHelpStepsThePaneThatHadTheFocusWhenItOpened() {
        Model help = Update.update(
                        focused(model, Scopes.BRANCHES.id()), new HelpMsg.Requested(Scopes.BRANCHES.id(), List.empty()))
                .model();

        Next next = Update.update(help, new HelpMsg.Chosen(new LayoutMsg.PaneStepped(5)));

        assertEquals(List.of(25, 25, 25), heights(next));
        assertTrue(next.model().popup().isEmpty());
    }

    @Test
    void zMaximizesTheFocusedPaneUntilZOnItAgain() {
        Model maximized = toggled(focused(model, Scopes.COMMITS.id()));

        assertEquals(Option.of(2), maximized.preferences().maximizedPane(Screen.REPOSITORY));
        assertEquals(Option.none(), maximized.preferences().maximizedPane(Screen.PULL_REQUESTS));
        // It stays maximized wherever the focus goes.
        assertEquals(
                Option.of(2),
                focused(maximized, Scopes.FILES.id()).preferences().maximizedPane(Screen.REPOSITORY));
        assertEquals(Option.none(), toggled(maximized).preferences().maximizedPane(Screen.REPOSITORY));
    }

    @Test
    void theKeysWorkOnTheFocusedPaneOfThePullRequestsScreen() {
        Model reviewRequested = focused(model.withScreen(Screen.PULL_REQUESTS), Scopes.PR_REVIEW_REQUESTED.id());

        Next taller = Update.update(reviewRequested, new LayoutMsg.PaneStepped(5));

        assertEquals(List.of(32, 37), taller.model().preferences().leftHeights(Screen.PULL_REQUESTS));
        assertEquals(
                Settings.DEFAULT_REPOSITORY_HEIGHTS,
                taller.model().preferences().leftHeights(Screen.REPOSITORY));
        assertEquals(Option.of(1), toggled(reviewRequested).preferences().maximizedPane(Screen.PULL_REQUESTS));
    }

    @Test
    void zOnAnotherPaneMaximizesThatOneInstead() {
        Model maximized = toggled(focused(model, Scopes.COMMITS.id()));

        Next files = Update.update(focused(maximized, Scopes.FILES.id()), new LayoutMsg.MaximizeToggled());

        assertEquals(Option.of(0), files.model().preferences().maximizedPane(Screen.REPOSITORY));
        assertEquals(List.empty(), files.cmds());
    }

    @Test
    void zOnTheContentPanelMakesTheLeftColumnAsNarrowAsItGetsApartFromAMaximizedPane() {
        Model narrow = toggled(content(toggled(focused(model, Scopes.COMMITS.id()))));

        assertEquals(Settings.MIN_WIDTH, narrow.preferences().leftWidth(Screen.REPOSITORY));
        assertEquals(30, narrow.preferences().keptWidth(Screen.REPOSITORY));
        assertEquals(Option.of(2), narrow.preferences().maximizedPane(Screen.REPOSITORY));
        assertEquals(30, toggled(narrow).preferences().leftWidth(Screen.REPOSITORY));
        assertEquals(Option.of(2), toggled(narrow).preferences().maximizedPane(Screen.REPOSITORY));
    }

    @Test
    void aWidthStepEndsTheMaximizedContentPanelFromTheWidthShown() {
        Next next = Update.update(toggled(content(model)), new LayoutMsg.Stepped(5));

        assertFalse(next.model().preferences().contentMaximized(Screen.REPOSITORY));
        assertEquals(20, next.model().preferences().settings().repositoryWidth());
    }

    @Test
    void aDragOfTheColumnBorderEndsTheMaximizedContentPanel() {
        Model dragged = Update.update(toggled(content(model)), new LayoutMsg.Resized(Screen.REPOSITORY, 25))
                .model();

        assertFalse(dragged.preferences().contentMaximized(Screen.REPOSITORY));
        assertEquals(25, dragged.preferences().leftWidth(Screen.REPOSITORY));
    }

    @Test
    void aStepEndsTheMaximizedPaneFromTheHeightsShownEachAtLeastItsLeastHeight() {
        // In 40 rows, the maximized files take 28, 70 percent, the others 3 each, under their least height: raised to
        // it, the files are 65 percent tall, and a step makes them 60.
        Model maximized = toggled(measured(focused(model, Scopes.FILES.id()), 40));

        Next next = Update.update(maximized, new LayoutMsg.PaneStepped(-5));

        assertEquals(Option.none(), next.model().preferences().maximizedPane(Screen.REPOSITORY));
        assertEquals(List.of(60, 15, 10), heights(next));
    }

    @Test
    void aStepThatChangesNoRowsStillEndsTheMaximizedPane() {
        Model maximized = toggled(measured(focused(model, Scopes.FILES.id()), 40));

        // The stash shows all it has: it gets no taller, but the panes keep the heights they were shown with.
        Next next = Update.update(focused(maximized, Scopes.STASH.id()), new LayoutMsg.PaneStepped(5));

        assertEquals(Option.none(), next.model().preferences().maximizedPane(Screen.REPOSITORY));
        assertEquals(List.of(65, 10, 10), heights(next));
    }

    @Test
    void aDragEndsTheMaximizedPaneFromTheHeightsShown() {
        Model maximized = toggled(measured(focused(model, Scopes.FILES.id()), 40));

        Model dragged = Update.update(maximized, new LayoutMsg.PaneResized(Screen.REPOSITORY, 0, 50))
                .model();

        assertEquals(Option.none(), dragged.preferences().maximizedPane(Screen.REPOSITORY));
        assertEquals(List.of(50, 25, 10), dragged.preferences().leftHeights(Screen.REPOSITORY));
    }

    @Test
    void theRowsOfTheColumnAreKeptForTheSessionOnly() {
        Next next = Update.update(model, new LayoutMsg.Measured(42));

        assertEquals(Option.of(42), next.model().preferences().columnRows());
        assertEquals(model.preferences().settings(), next.model().preferences().settings());
        assertEquals(List.empty(), next.cmds());
    }
}
