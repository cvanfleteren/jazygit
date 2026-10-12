package net.vanfleteren.jazygit.ui;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.Fetching;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The line saying which remote is being fetched from.
 */
class FetchLineTest {

    @Test
    void showsTheRemoteBeingFetchedFrom() throws Exception {
        assertEquals("Fetching origin",
                rendered(TestModels.loaded().withFetching(Option.of(new Fetching(Option.of("origin"), List.empty())))));
    }

    @Test
    void isBlankWhileNoRemoteIsBeingFetchedFrom() throws Exception {
        assertEquals("", rendered(TestModels.loaded()));
        assertEquals("", rendered(TestModels.loaded().withFetching(Option.of(Fetching.LISTING))));
    }

    private static String rendered(Model model) throws Exception {
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> FetchLine.render(model))) {
            return RenderedText.of(runner, () -> FetchLine.render(model)).strip();
        }
    }
}
