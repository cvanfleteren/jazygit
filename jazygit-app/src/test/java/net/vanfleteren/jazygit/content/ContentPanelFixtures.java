package net.vanfleteren.jazygit.content;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.toolkit.app.ToolkitTestRunner;
import io.vavr.collection.List;
import java.time.Instant;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.settings.DiffStyle;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.diff.*;
import net.vanfleteren.jazygit.state.load.*;
import net.vanfleteren.jazygit.state.preferences.*;
import net.vanfleteren.jazygit.state.view.*;
import net.vanfleteren.jazygit.ui.theme.Theme;

/**
 * What the tests of the content panel share: commits and models that show them in it.
 */
final class ContentPanelFixtures {

    private ContentPanelFixtures() {}

    static Commit plain(String sha, String... parents) {
        return Fixtures.commit(
                new Sha(sha),
                "Ada",
                "a@e.com",
                Instant.EPOCH,
                sha,
                "",
                List.of(parents).map(Sha::new));
    }

    /**
     * The model with commit aaaa highlighted and its detail, {@code changes}, loaded.
     */
    static Model showingCommit(String changes) {
        return Update.update(
                        Update.update(TestModels.loaded(), new SelectionMsg.CommitSelected(new Sha("aaaa")))
                                .model(),
                        new LoadMsg.CommitDetailLoaded(new Sha("aaaa"), DiffOptions.DEFAULT, DiffText.of(changes)))
                .model();
    }

    // The added lines in green, the bars of the files in blue.
    static final Theme.SpanStyles GREEN_ADDED = cssClass -> switch (cssClass) {
        case Theme.Classes.DIFF_ADDED_LINE -> Style.EMPTY.bg(Color.GREEN);
        case Theme.Classes.DIFF_FILE_HEADER -> Style.EMPTY.bg(Color.BLUE);
        default -> Style.EMPTY;
    };

    /**
     * The model with a commit selected that adds {@code schema.sql} with {@code lines}, drawn rich.
     */
    static Model showingRichCommit(String lines) {
        Model rich = TestModels.loaded()
                .mapPreferences(preferences ->
                        preferences.withSettings(preferences.settings().withDiffStyle(DiffStyle.RICH)));
        int count = lines.split("\n").length;
        return Update.update(
                        Update.update(rich, new SelectionMsg.CommitSelected(new Sha("aaaa")))
                                .model(),
                        new LoadMsg.CommitDetailLoaded(
                                new Sha("aaaa"),
                                rich.preferences().diffOptions(),
                                DiffText.of(String.join(
                                        "\n",
                                        "diff --git a/schema.sql b/schema.sql",
                                        "new file mode 100644",
                                        "--- /dev/null",
                                        "+++ b/schema.sql",
                                        "@@ -0,0 +1," + count + " @@",
                                        lines))))
                .model();
    }

    /**
     * The cells {@code element} draws on a screen {@code width} columns wide and 30 high, on the render thread.
     */
    static dev.tamboui.buffer.Buffer drawn(
            ToolkitTestRunner testRunner,
            java.util.function.Supplier<? extends dev.tamboui.toolkit.element.Element> element,
            int width)
            throws Exception {
        dev.tamboui.layout.Rect screen = new dev.tamboui.layout.Rect(0, 0, width, 30);
        java.util.concurrent.CompletableFuture<dev.tamboui.buffer.Buffer> drawn =
                new java.util.concurrent.CompletableFuture<>();
        testRunner.runner().runOnRenderThread(() -> {
            dev.tamboui.buffer.Buffer buffer = dev.tamboui.buffer.Buffer.empty(screen);
            element.get()
                    .render(
                            dev.tamboui.terminal.Frame.forTesting(buffer),
                            screen,
                            dev.tamboui.toolkit.element.RenderContext.empty());
            drawn.complete(buffer);
        });
        return drawn.get(5, java.util.concurrent.TimeUnit.SECONDS);
    }

    /**
     * Asserts the background of the row of {@code buffer} that shows {@code text} is {@code colour} in one stretch, up
     * to column {@code last}.
     */
    static void assertBackgroundTo(Color colour, String text, int last, dev.tamboui.buffer.Buffer buffer) {
        int width = buffer.area().width();
        int row = List.range(0, buffer.area().height())
                .find(y -> List.range(0, width)
                        .map(x -> buffer.get(x, y).symbol())
                        .mkString()
                        .contains(text))
                .get();
        List<Integer> coloured =
                List.range(0, width).filter(x -> buffer.get(x, row).style().bg().equals(java.util.Optional.of(colour)));
        assertEquals(last, coloured.last(), coloured.toString());
        assertEquals(coloured.last() - coloured.head() + 1, coloured.size(), coloured.toString());
    }

    /**
     * The row of {@code screen} that shows {@code text}.
     */
    static int row(String screen, String text) {
        return List.of(screen.split("\n")).indexWhere(line -> line.contains(text));
    }
}
