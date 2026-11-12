package net.vanfleteren.jazygit.ui.widgets;

import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.control.Option;
import java.util.ArrayList;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.operation.OperationCmd;
import net.vanfleteren.jazygit.state.popup.ConfirmablePopup;
import net.vanfleteren.jazygit.state.popup.Popup;
import net.vanfleteren.jazygit.state.popup.PopupMsg;
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.SettledPilot;
import org.junit.jupiter.api.Test;

class ConfirmDialogTest {

    private static final String LONG = "This is a long question that needs several lines at the width of the dialog, "
            + "so that the hint below it must still stay visible after the message has been wrapped. Really?";
    private final java.util.List<Msg> dispatched = new ArrayList<>();

    private static Model open() {
        return TestModels.loaded().openPopup(new AskPopup("foo"));
    }

    private ConfirmDialog<AskPopup> dialog(String message) {
        return new ConfirmDialog<>(spec(message), dispatched::add);
    }

    private static ConfirmDialog.Spec<AskPopup> spec(String message) {
        return new ConfirmDialog.Spec<>(
                "ask", AskPopup.class, popup -> "Delete " + popup.thing(), popup -> message, Yes::new);
    }

    @Test
    void closedModelShowsNoPopup() {
        assertTrue(dialog("Sure?").render(TestModels.loaded()).isEmpty());
    }

    @Test
    void showsTitleMessageAndTheStandardHint() throws Exception {
        ConfirmDialog<AskPopup> dialog = dialog("Are you sure?");
        try (ToolkitTestRunner runner = start(dialog)) {
            String screen = RenderedText.ofLarge(runner, () -> view(dialog));
            assertTrue(screen.contains("Delete foo"), screen);
            assertTrue(screen.contains("Are you sure?"), screen);
            assertTrue(screen.contains("(enter) confirm   (esc) cancel"), screen);
        }
    }

    @Test
    void aLongMessageWrapsAndKeepsTheHintVisible() throws Exception {
        ConfirmDialog<AskPopup> dialog = dialog(LONG);
        try (ToolkitTestRunner runner = start(dialog)) {
            String screen = RenderedText.ofLarge(runner, () -> view(dialog));
            assertTrue(screen.contains("Really?"), screen);
            assertTrue(screen.contains("(enter) confirm   (esc) cancel"), screen);
        }
    }

    @Test
    void enterConfirmsAndEscapeCancels() throws Exception {
        ConfirmDialog<AskPopup> dialog = dialog("Sure?");
        try (ToolkitTestRunner runner = start(dialog)) {
            SettledPilot.of(runner).press(KeyCode.ENTER);
            SettledPilot.of(runner).pause();
            SettledPilot.of(runner).press(KeyCode.ESCAPE);
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new Yes(), new PopupMsg.Dismissed(AskPopup.class)), dispatched);
    }

    @Test
    void escapeSendsWhatTheDialogIsCancelledBy() throws Exception {
        ConfirmDialog<AskPopup> dialog = new ConfirmDialog<>(spec("Sure?").cancelledBy(No::new), dispatched::add);
        try (ToolkitTestRunner runner = start(dialog)) {
            SettledPilot.of(runner).press(KeyCode.ESCAPE);
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new No()), dispatched);
    }

    @Test
    void aMessageOfShortWordsIsSixtyWide() {
        assertEquals(60, ConfirmDialog.width("Title", LONG));
    }

    @Test
    void aLongerWordWidensTheDialogToKeepItOnOneLine() {
        assertEquals(72, ConfirmDialog.width("Title", "At\n" + "x".repeat(70) + "\nok?"));
    }

    @Test
    void aLongerTitleWidensTheDialogToShowItWhole() {
        // A title of 81 columns, and the border on both sides.
        assertEquals(83, ConfirmDialog.width("Push " + "x".repeat(70) + " first", "Push it?"));
    }

    @Test
    void theDialogIsNoWiderThanAHundred() {
        assertEquals(100, ConfirmDialog.width("Title", "x".repeat(200)));
        assertEquals(100, ConfirmDialog.width("x".repeat(200), "Push it?"));
    }

    @Test
    void aWordOfWideCharactersIsMeasuredInColumns() {
        // 35 characters of two columns each.
        assertEquals(72, ConfirmDialog.width("Title", "漢".repeat(35)));
    }

    @Test
    void aScreenNarrowerThanTheDialogStillShowsTheWholeMessage() throws Exception {
        ConfirmDialog<AskPopup> dialog = dialog("At\n" + "x".repeat(90) + "\n\nReally?");
        try (ToolkitTestRunner runner = start(dialog)) {
            String screen = RenderedText.ofSize(runner, () -> view(dialog), 60, 20);
            assertTrue(screen.contains("Really?"), screen);
            assertTrue(screen.contains("(enter) confirm   (esc) cancel"), screen);
        }
    }

    private ToolkitTestRunner start(ConfirmDialog<AskPopup> dialog) throws Exception {
        ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> view(dialog));
        SettledPilot.focus(runner, "ask");
        RenderedText.of(runner, () -> view(dialog));
        return runner;
    }

    private Element view(ConfirmDialog<AskPopup> dialog) {
        return stack(text("background"), dialog.render(open()).get());
    }

    @Test
    void aConfirmingDialogConfirmsAndDismissesItsPopup() {
        ConfirmDialog.Spec<SurePopup> spec =
                ConfirmDialog.Spec.confirming("sure", SurePopup.class, popup -> "Sure", popup -> "Sure?");

        assertEquals(new PopupMsg.Confirmed(SurePopup.class), spec.onConfirm().get());
        assertEquals(new PopupMsg.Dismissed(SurePopup.class), spec.onCancel().get());
    }

    private record AskPopup(String thing) implements Popup {}

    private record SurePopup() implements ConfirmablePopup {
        @Override
        public Option<OperationCmd> confirm() {
            return Option.none();
        }
    }

    private record Yes() implements Msg {
        @Override
        public net.vanfleteren.jazygit.state.Update.Next apply(Model model) {
            return net.vanfleteren.jazygit.state.Update.Next.of(model);
        }
    }

    private record No() implements Msg {
        @Override
        public net.vanfleteren.jazygit.state.Update.Next apply(Model model) {
            return net.vanfleteren.jazygit.state.Update.Next.of(model);
        }
    }
}
