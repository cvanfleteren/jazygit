package net.vanfleteren.jazygit.kernel.state.view;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update;
import org.junit.jupiter.api.Test;

/**
 * Tests on the line to type a line number in (ADR 140).
 */
class LineNumberMsgTest {

    @Test
    void theLineOpensEmptyKeepsWhatIsTypedAndClosesToNothing() {
        Model opened =
                Update.update(Model.initial("repo"), new LineNumberMsg.Opened()).model();
        assertEquals(Option.some(""), opened.lineNumber());

        Model typed = Update.update(opened, new LineNumberMsg.Edited("42")).model();
        assertEquals(Option.some("42"), typed.lineNumber());

        assertEquals(
                Option.none(),
                Update.update(typed, new LineNumberMsg.Closed()).model().lineNumber());
    }

    @Test
    void textTypedWhileTheLineIsClosedOpensNothing() {
        assertEquals(
                Option.none(),
                Update.update(Model.initial("repo"), new LineNumberMsg.Edited("4"))
                        .model()
                        .lineNumber());
    }
}
