package net.vanfleteren.jazygit.kernel.ui.content;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.state.AreaId;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.PaneId;
import net.vanfleteren.jazygit.kernel.ui.Feature;
import org.junit.jupiter.api.Test;

/**
 * The sources declare the areas of the content panel (ADR 154), and a feature lists its sources.
 */
class ContentSourcesTest {

    private static final ContentArea NOTE = ContentArea.of(new AreaId("note"), new PaneId("files"));
    private static final ContentArea DIFF = ContentArea.of(new AreaId("diff"), new PaneId("files"));
    private static final ContentArea LOG = ContentArea.of(new AreaId("log"), new PaneId("branches"));

    private static final ContentSource DIFFS = new Declares(List.of(DIFF, NOTE));
    private static final ContentSource FILE = new Declares(List.of(NOTE));
    private static final ContentSource LOGS = new Declares(List.of(LOG));

    @Test
    void anAreaTwoSourcesDeclareIsOneArea() {
        assertEquals(List.of(DIFF, NOTE, LOG), new ContentSources(List.of(DIFFS, FILE, LOGS)).areas());
    }

    @Test
    void anAreaIsFoundByItsId() {
        ContentSources sources = new ContentSources(List.of(DIFFS, LOGS));

        assertEquals(Option.of(LOG), sources.area(new AreaId("log")));
        assertEquals(Option.of(DIFF), sources.focusable("diff"));
        assertEquals(Option.none(), sources.focusable("files"));
    }

    /**
     * Without the app, no source is on the classpath.
     */
    @Test
    void withoutTheAppThereAreNoSources() {
        assertEquals(List.empty(), ContentSources.standard().sources());
    }

    @Test
    void aFeatureShowsItsSourcesInOrder() {
        Feature feature = Feature.of().showing(DIFFS).showing(FILE, LOGS);

        assertEquals(List.of(DIFFS, FILE, LOGS), feature.contents());
        assertEquals(List.empty(), Feature.of().contents());
    }

    @Test
    void theContentMirrorsTheLeftPanesOfTheRepositoryScreen() {
        assertEquals(Option.of(Mirrored.BRANCHES), Mirrored.of(new PaneId("branches")));
        assertEquals(Option.none(), Mirrored.of(new PaneId("pullRequests")));
    }

    /**
     * A source that shows nothing, but declares {@code areas}.
     */
    private record Declares(List<ContentArea> areas) implements ContentSource {

        @Override
        public Option<Shown> shown(Model model, Mirrored pane) {
            return Option.none();
        }
    }
}
