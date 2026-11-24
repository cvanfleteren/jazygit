package net.vanfleteren.jazygit.kernel.content;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.state.AreaId;
import net.vanfleteren.jazygit.kernel.state.Focus;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.PaneId;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.ui.content.ContentArea;
import net.vanfleteren.jazygit.kernel.ui.content.ContentSource;
import net.vanfleteren.jazygit.kernel.ui.content.ContentSources;
import net.vanfleteren.jazygit.kernel.ui.content.Headed;
import net.vanfleteren.jazygit.kernel.ui.content.Heading;
import net.vanfleteren.jazygit.kernel.ui.content.Mirrored;
import net.vanfleteren.jazygit.kernel.ui.content.Shown;
import net.vanfleteren.jazygit.kernel.ui.content.Tone;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;
import net.vanfleteren.jazygit.kernel.ui.text.Placeholders;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import org.junit.jupiter.api.Test;

/**
 * What the content panel shows is what the first source that answers gives (ADR 154): a feature's source, asked
 * before the content panel's own, takes the place of theirs.
 */
class ContentFoldTest {

    private static final Model MODEL = TestModels.loaded();

    private static final ContentArea MINE = ContentArea.of(new AreaId("mine"), Scopes.BRANCHES.pane());

    @Test
    void aSourceThatAnswersComesBeforeTheContentPanelsOwn() {
        ContentSources sources = new ContentSources(
                List.<ContentSource>of(new Says(Mirrored.BRANCHES)).appendAll(PanelSources.ALL));

        assertEquals(said(Mirrored.BRANCHES), ContentView.of(MODEL, Option.of(Scopes.BRANCHES.pane()), sources));
    }

    @Test
    void aSourceThatDoesNotAnswerLeavesItToTheNext() {
        ContentSources own = new ContentSources(PanelSources.ALL);
        ContentSources sources = new ContentSources(
                List.<ContentSource>of(new Says(Mirrored.BRANCHES)).appendAll(PanelSources.ALL));

        assertEquals(
                ContentView.of(MODEL, Option.of(PaneId.COMMITS), own),
                ContentView.of(MODEL, Option.of(PaneId.COMMITS), sources));
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
                ContentView.of(MODEL, Option.of(Scopes.BRANCHES.pane()), new ContentSources(List.empty())));
    }

    /**
     * A content panel given its sources has the areas they declare, and none of the app's, so a feature's tests can
     * draw it with its own source only.
     */
    @Test
    void aContentPanelHasTheAreasOfTheSourcesItIsGiven() {
        ContentPanel panel = new ContentPanel(
                new ContentSources(List.of(new InArea())), Theme.SpanStyles.NONE, () -> false, id -> {}, msg -> {});

        assertEquals(Option.of(MINE.id()), panel.areaOfPane(MODEL, Scopes.BRANCHES.pane()));
        assertEquals(Option.of(Scopes.BRANCHES.pane()), panel.paneOfArea(MODEL, MINE.id()));
        assertEquals(Option.none(), panel.paneOfArea(MODEL, AreaId.LOG));
    }

    /**
     * Each source that heads what is shown next to a pane puts its heading above it, in the order of the sources,
     * whichever source shows it (ADR 187).
     */
    @Test
    void theSourcesPutWhatTheySayAboveWhatAnotherShows() {
        ContentSources sources = new ContentSources(List.of(new Heads("one"), new InArea(), new Heads("two")));

        Shown shown = ContentView.of(MODEL, Option.of(Scopes.BRANCHES.pane()), sources);

        assertEquals(
                new Headed(saying("one"), new Headed(saying("two"), new NoteBody("mine", Tone.DIM))),
                shown.areas().head().body());
    }

    @Test
    void aMessageHasNoHeading() {
        ContentSources sources = new ContentSources(List.of(new Heads("one"), new Says(Mirrored.BRANCHES)));

        assertEquals(said(Mirrored.BRANCHES), ContentView.of(MODEL, Option.of(Scopes.BRANCHES.pane()), sources));
    }

    private static Heading saying(String text) {
        return new Heading(List.of(List.of(Heading.Part.of(text))));
    }

    private static Shown said(Mirrored pane) {
        return new Shown.Message("mine", String.valueOf(pane), Tone.DIM);
    }

    /**
     * A source that answers for {@code pane} only, saying which pane it answered for.
     */
    private record Says(Mirrored pane) implements ContentSource {

        @Override
        public Option<Shown> shown(Model model, Mirrored mirrored) {
            return Option.when(mirrored.equals(pane), () -> said(mirrored));
        }

        @Override
        public List<ContentArea> areas() {
            return List.of(MINE);
        }
    }

    /**
     * A source that shows nothing and puts {@code text} above what another shows next to the branches.
     */
    private record Heads(String text) implements ContentSource {

        @Override
        public Option<Shown> shown(Model model, Mirrored mirrored) {
            return Option.none();
        }

        @Override
        public Option<Heading> heading(Model model, Mirrored mirrored) {
            return Option.when(mirrored.equals(Mirrored.BRANCHES), () -> saying(text));
        }

        @Override
        public List<ContentArea> areas() {
            return List.empty();
        }
    }

    /**
     * A source that shows a note in its area next to the branches.
     */
    private record InArea() implements ContentSource {

        @Override
        public Option<Shown> shown(Model model, Mirrored mirrored) {
            return Option.when(
                    mirrored.equals(Mirrored.BRANCHES),
                    () -> new Shown.Area(MINE, "mine", new FileNote.Note("mine"), new NoteBody("mine", Tone.DIM)));
        }

        @Override
        public List<ContentArea> areas() {
            return List.of(MINE);
        }
    }
}
