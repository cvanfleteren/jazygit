package net.vanfleteren.jazygit.ui.widgets;

import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.ArrayList;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.ui.RenderedText;
import org.junit.jupiter.api.Test;

class PickDialogTest {

    private static final List<String> BRANCHES = List.of("main", "develop", "release");
    private final java.util.List<Msg> dispatched = new ArrayList<>();
    private final PickDialog<BranchPopup> dialog = new PickDialog<>(
            new PickDialog.Spec<>(
                    "pick",
                    BranchPopup.class,
                    popup -> "Merge into",
                    BranchPopup::branches,
                    Picked::new,
                    () -> new Picked("cancel")),
            dispatched::add);

    @Test
    void closedModelShowsNoPopup() {
        assertTrue(dialog.render(TestModels.loaded()).isEmpty());
    }

    @Test
    void listsTheNamesOfThePopup() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            String screen = RenderedText.ofLarge(runner, this::view);
            assertTrue(screen.contains("Merge into"), screen);
            assertTrue(screen.indexOf("main") < screen.indexOf("develop"), screen);
            assertTrue(screen.indexOf("develop") < screen.indexOf("release"), screen);
            assertTrue(screen.contains("(enter) pick"), screen);
        }
    }

    @Test
    void aNameIsPickedWithTheArrowsAndEnterAndEscapeCancels() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            runner.pilot().press(KeyCode.ENTER);
            runner.pilot().pause();
            runner.pilot().press(KeyCode.DOWN);
            runner.pilot().press(KeyCode.DOWN);
            runner.pilot().press(KeyCode.ENTER);
            runner.pilot().pause();
            runner.pilot().press(KeyCode.ESCAPE);
            runner.pilot().pause();
        }

        assertEquals(java.util.List.of(new Picked("main"), new Picked("release"), new Picked("cancel")), dispatched);
    }

    @Test
    void withoutNamesItSaysSoAndOnlyCancels() throws Exception {
        PickDialog<BranchPopup> empty = new PickDialog<>(
                new PickDialog.Spec<>(
                        "pick",
                        BranchPopup.class,
                        popup -> "Merge into",
                        BranchPopup::branches,
                        Picked::new,
                        () -> new Picked("cancel"),
                        Option.of("dialog.recentRepos.none")),
                dispatched::add);
        Supplier<Element> view = () -> stack(
                text("background"),
                empty.render(TestModels.loaded().openPopup(new BranchPopup(List.empty())))
                        .get());

        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(view)) {
            runner.runner().focusManager().setFocus("pick");
            String screen = RenderedText.ofLarge(runner, view);
            assertTrue(screen.contains("No other recent repositories"), screen);
            runner.pilot().press(KeyCode.ENTER);
            runner.pilot().pause();
            runner.pilot().press(KeyCode.ESCAPE);
            runner.pilot().pause();
        }

        assertEquals(java.util.List.of(new Picked("cancel")), dispatched);
    }

    private ToolkitTestRunner start() throws Exception {
        ToolkitTestRunner runner = ToolkitTestRunner.runTest(this::view);
        runner.runner().focusManager().setFocus("pick");
        RenderedText.of(runner, this::view);
        return runner;
    }

    private Element view() {
        return stack(
                text("background"),
                dialog.render(TestModels.loaded().openPopup(new BranchPopup(BRANCHES)))
                        .get());
    }

    private record BranchPopup(List<String> branches) implements Popup {}

    private record Picked(String what) implements Msg {
        @Override
        public Update.Next apply(Model model) {
            return Update.Next.of(model);
        }
    }
}
