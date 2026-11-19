package net.vanfleteren.jazygit.content;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.AreaId;
import net.vanfleteren.jazygit.state.Focus;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.ui.branches.BranchesPanel;
import net.vanfleteren.jazygit.ui.commits.CommitsPanel;
import net.vanfleteren.jazygit.ui.content.ContentArea;
import net.vanfleteren.jazygit.ui.content.ContentSource;
import net.vanfleteren.jazygit.ui.content.ContentSources;
import net.vanfleteren.jazygit.ui.content.Mirrored;
import net.vanfleteren.jazygit.ui.content.Shown;
import net.vanfleteren.jazygit.ui.content.Tone;
import net.vanfleteren.jazygit.ui.text.Placeholders;
import net.vanfleteren.jazygit.ui.theme.Theme;
import org.junit.jupiter.api.Test;

/**
 * What the content panel shows is what the first source that answers gives (ADR 154): a feature's source, asked
 * before the content panel's own, takes the place of theirs.
 */
class ContentFoldTest {

    private static final Model MODEL = TestModels.loaded();

    private static final ContentArea MINE = ContentArea.of(new AreaId("mine"), BranchesPanel.PANE);

    @Test
    void aSourceThatAnswersComesBeforeTheContentPanelsOwn() {
        ContentSources sources = new ContentSources(
                List.<ContentSource>of(new Says(Mirrored.BRANCHES)).appendAll(PanelSources.ALL));

        assertEquals(said(Mirrored.BRANCHES), ContentView.of(MODEL, Option.of(BranchesPanel.PANE), sources));
    }

    @Test
    void aSourceThatDoesNotAnswerLeavesItToTheNext() {
        ContentSources own = new ContentSources(PanelSources.ALL);
        ContentSources sources = new ContentSources(
                List.<ContentSource>of(new Says(Mirrored.BRANCHES)).appendAll(PanelSources.ALL));

        assertEquals(
                ContentView.of(MODEL, Option.of(CommitsPanel.PANE), own),
                ContentView.of(MODEL, Option.of(CommitsPanel.PANE), sources));
    }

    /**
     * An area a feature's source declares mirrors its pane, as the content panel's own areas do.
     */
    @Test
    void anAreaOfASourceMirrorsItsPane() {
        ContentSources sources = new ContentSources(List.of(new Says(Mirrored.BRANCHES)));

        assertEquals(said(Mirrored.BRANCHES), ContentView.of(MODEL, Option.<Focus>of(MINE.id()), sources));
    }

    @Test
    void withoutASourceThatAnswersItIsLoading() {
        assertEquals(
                new Shown.Message("", Placeholders.loading(), Tone.DIM),
                ContentView.of(MODEL, Option.of(BranchesPanel.PANE), new ContentSources(List.empty())));
    }

    /**
     * A content panel given its sources has the areas they declare, and none of the app's, so a feature's tests can
     * draw it with its own source only.
     */
    @Test
    void aContentPanelHasTheAreasOfTheSourcesItIsGiven() {
        ContentPanel panel = new ContentPanel(
                new ContentSources(List.of(new InArea())), Theme.SpanStyles.NONE, () -> false, id -> {}, msg -> {});

        assertEquals(Option.of(MINE.id()), panel.areaOfPane(MODEL, BranchesPanel.PANE));
        assertEquals(Option.of(BranchesPanel.PANE), panel.paneOfArea(MODEL, MINE.id()));
        assertEquals(Option.none(), panel.paneOfArea(MODEL, AreaId.LOG));
    }

    private static Shown said(Mirrored pane) {
        return new Shown.Message("mine", pane.name(), Tone.DIM);
    }

    /**
     * A source that answers for {@code pane} only, saying which pane it answered for.
     */
    private record Says(Mirrored pane) implements ContentSource {

        @Override
        public Option<Shown> shown(Model model, Mirrored mirrored) {
            return Option.when(mirrored == pane, () -> said(mirrored));
        }

        @Override
        public List<ContentArea> areas() {
            return List.of(MINE);
        }
    }

    /**
     * A source that shows a note in its area next to the branches.
     */
    private record InArea() implements ContentSource {

        @Override
        public Option<Shown> shown(Model model, Mirrored mirrored) {
            return Option.when(
                    mirrored == Mirrored.BRANCHES,
                    () -> new Shown.Area(MINE, "mine", new FileNote.Note("mine"), new NoteBody("mine", Tone.DIM)));
        }

        @Override
        public List<ContentArea> areas() {
            return List.of(MINE);
        }
    }
}
