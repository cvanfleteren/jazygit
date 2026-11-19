package net.vanfleteren.jazygit.kernel.state.popup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update;
import org.junit.jupiter.api.Test;

/**
 * ssh asking the user for a secret (ADR 150): its popup goes over the one that is open, which comes back as it was once
 * it closes, and stays on top of one that opens meanwhile.
 */
class SshPromptPopupTest {

    private static final String PROMPT = "Enter passphrase for key '/home/u/.ssh/id_ed25519': ";
    private static final ListCursor MOVED = new ListCursor(Option.of(2), Option.of("ma"));

    private final Model typing = Update.update(
                    TestModels.loaded().openPopup(new Typing("half a commit message")),
                    new PopupMsg.ListCursorChanged(MOVED))
            .model();

    @Test
    void askingOpensOverThePopupThatIsOpen() {
        Model asking = asked(typing, 1);

        assertEquals(
                Option.of(new SshPromptPopup(1, PROMPT, Option.of(new Typing("half a commit message")), MOVED)),
                asking.popup());
        assertEquals(ListCursor.START, asking.listCursor());
    }

    @Test
    void thePopupUnderItComesBackAsItWasOnceItCloses() {
        Model closed =
                Update.update(asked(typing, 1), new SshPromptMsg.Closed(1)).model();

        assertEquals(typing.popup(), closed.popup());
        assertEquals(MOVED, closed.listCursor());
    }

    @Test
    void withNothingUnderItNothingIsOpenOnceItCloses() {
        Model closed = Update.update(asked(TestModels.loaded(), 1), new SshPromptMsg.Closed(1))
                .model();

        assertEquals(Option.none(), closed.popup());
    }

    @Test
    void anotherQuestionClosesNothing() {
        Model asking = asked(typing, 2);

        assertEquals(asking, Update.update(asking, new SshPromptMsg.Closed(1)).model());
    }

    @Test
    void aPopupThatOpensMeanwhileGoesUnderIt() {
        Model asking = asked(typing, 1).openPopup(new Other());

        assertEquals(
                Option.of(new SshPromptPopup(1, PROMPT, Option.of(new Other()), ListCursor.START)), asking.popup());
        assertEquals(
                Option.of(new Other()),
                Update.update(asking, new SshPromptMsg.Closed(1)).model().popup());
    }

    @Test
    void thePopupUnderItClosesUnderIt() {
        Model asking = asked(typing, 1).withoutPopup(Typing.class);

        assertEquals(Option.of(new SshPromptPopup(1, PROMPT, Option.none(), ListCursor.START)), asking.popup());
        assertEquals(asking, asking.withoutPopup(Other.class));
    }

    @Test
    void aSecondQuestionKeepsWhatIsUnderTheFirst() {
        Model asking = asked(asked(typing, 1), 2);

        assertEquals(
                Option.of(new SshPromptPopup(2, PROMPT, Option.of(new Typing("half a commit message")), MOVED)),
                asking.popup());
    }

    @Test
    void itHoldsTheInputOfThePopupUnderIt() {
        assertTrue(asked(typing, 1).popup().get().holdsInput());
        assertTrue(!asked(TestModels.loaded(), 1).popup().get().holdsInput());
    }

    private static Model asked(Model model, long request) {
        return Update.update(model, new SshPromptMsg.Asked(request, PROMPT)).model();
    }

    private record Typing(String text) implements Popup {

        @Override
        public boolean holdsInput() {
            return true;
        }
    }

    private record Other() implements Popup {}
}
