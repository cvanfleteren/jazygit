package net.vanfleteren.jazygit.state.view;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import org.junit.jupiter.api.Test;

/**
 * The search of the content panel as the user types, keeps and clears it (ADR 126).
 */
class ContentSearchMsgTest {

    private static Model after(Model model, Msg... msgs) {
        Model next = model;
        for (Msg msg : msgs) {
            next = Update.update(next, msg).model();
        }
        return next;
    }

    @Test
    void nothingIsSearchedWhileTheLineIsOpenAndEnterKeepsWhatItHolds() {
        Model typing = after(TestModels.loaded(), new ContentSearchMsg.Opened(), new ContentSearchMsg.Edited("foo"));
        assertEquals(Option.none(), ContentSearch.query(typing.get(ContentSearch.SLICE)));

        Model kept = after(typing, new ContentSearchMsg.Submitted());
        assertEquals(Option.of("foo"), ContentSearch.query(kept.get(ContentSearch.SLICE)));
    }

    @Test
    void theLineOpensWithTheQueryKeptAndEscapePutsItBack() {
        Model kept = after(
                TestModels.loaded(),
                new ContentSearchMsg.Opened(),
                new ContentSearchMsg.Edited("foo"),
                new ContentSearchMsg.Submitted());

        Model reopened = after(kept, new ContentSearchMsg.Opened());
        assertEquals("foo", reopened.get(ContentSearch.SLICE).get().text());

        Model cancelled = after(reopened, new ContentSearchMsg.Edited("bar"), new ContentSearchMsg.Cancelled());
        assertEquals(Option.of("foo"), ContentSearch.query(cancelled.get(ContentSearch.SLICE)));
    }

    @Test
    void anEmptyLineOrEscapeWithoutASearchBeforeLeavesNone() {
        assertEquals(
                Option.none(),
                after(TestModels.loaded(), new ContentSearchMsg.Opened(), new ContentSearchMsg.Submitted())
                        .get(ContentSearch.SLICE));
        assertEquals(
                Option.none(),
                after(
                                TestModels.loaded(),
                                new ContentSearchMsg.Opened(),
                                new ContentSearchMsg.Edited("x"),
                                new ContentSearchMsg.Cancelled())
                        .get(ContentSearch.SLICE));
    }

    @Test
    void clearedTheSearchIsGone() {
        Model kept = after(
                TestModels.loaded(),
                new ContentSearchMsg.Opened(),
                new ContentSearchMsg.Edited("foo"),
                new ContentSearchMsg.Submitted(),
                new ContentSearchMsg.Cleared());

        assertEquals(Option.none(), kept.get(ContentSearch.SLICE));
    }
}
