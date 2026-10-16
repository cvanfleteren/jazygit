package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.Fetching;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import org.junit.jupiter.api.Test;

/**
 * The line saying which remote is being fetched from, or that the pull requests are being read.
 */
class FetchLineTest {

    private static String rendered(Model model) throws Exception {
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> FetchLine.render(model))) {
            return RenderedText.of(runner, () -> FetchLine.render(model)).strip();
        }
    }

    @Test
    void showsTheRemoteBeingFetchedFrom() throws Exception {
        assertEquals(
                "Fetching origin",
                rendered(TestModels.loaded().withFetching(Option.of(new Fetching(Option.of("origin"), List.empty())))));
    }

    @Test
    void showsThePullRequestsBeingReadWhenNoRemoteIsBeingFetchedFrom() throws Exception {
        Model reading = TestModels.loaded().withReadingPullRequests(1);

        assertEquals("Reading pull requests from GitHub", rendered(reading));
        assertEquals(
                "Fetching origin",
                rendered(reading.withFetching(Option.of(new Fetching(Option.of("origin"), List.empty())))));
    }

    @Test
    void isBlankWhileNoRemoteIsBeingFetchedFrom() throws Exception {
        assertEquals("", rendered(TestModels.loaded()));
        assertEquals("", rendered(TestModels.loaded().withFetching(Option.of(Fetching.LISTING))));
    }
}
