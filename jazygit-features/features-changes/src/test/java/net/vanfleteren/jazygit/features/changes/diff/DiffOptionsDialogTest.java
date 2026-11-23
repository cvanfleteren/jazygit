package net.vanfleteren.jazygit.features.changes.diff;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.DiffContext;
import net.vanfleteren.jazygit.kernel.settings.DiffStyle;
import net.vanfleteren.jazygit.kernel.state.Msg;
import org.junit.jupiter.api.Test;

class DiffOptionsDialogTest {

    private static final DiffOptionsPopup POPUP =
            new DiffOptionsPopup(DiffStyle.RICH, true, false, false, DiffContext.DEFAULT, DiffFilters.NONE);

    @Test
    void eachOptionHasALetterThatChangesItAtOnce() {
        // After ctrl+d, a letter changes its option: ctrl+d s steps the style, ctrl+d p switches the pairs.
        assertEquals(
                List.of(
                        keyed('s', new DiffOptionsMsg.StyleChosen(DiffStyle.SPLIT)),
                        keyed('c', new DiffOptionsMsg.ContextWidened()),
                        keyed('w', new DiffOptionsMsg.WordsToggled()),
                        keyed('p', new DiffOptionsMsg.PairsToggled()),
                        keyed('i', new DiffOptionsMsg.WhitespaceToggled()),
                        keyed('f', new DiffFiltersMsg.Requested())),
                DiffOptionsDialog.rows(POPUP).map(row -> Tuple.<Option<Character>, Msg>of(row.key(), row.value())));
    }

    @Test
    void theFiltersRowIsStillTheOneThePopupOfTheFiltersGoesBackTo() {
        assertEquals(
                Option.of('f'),
                DiffOptionsDialog.rows(POPUP).get(DiffOptionsDialog.FILTERS_ROW).key());
    }

    private static Tuple2<Option<Character>, Msg> keyed(char key, Msg msg) {
        return Tuple.of(Option.of(key), msg);
    }
}
