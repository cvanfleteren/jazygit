package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.feature.layout.LayoutMsg;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.Stash;
import net.vanfleteren.jazygit.state.*;
import org.junit.jupiter.api.Test;

class LeftColumnTest {

    private static Model withStashes(int count) {
        List<Stash> stashes = List.range(0, count)
                .map(i -> new Stash("stash@{" + i + "}", new Sha("s" + i), "wip " + i, Option.none(), Instant.EPOCH));
        return Update.update(TestModels.loaded(), new LoadMsg.StashesLoaded(stashes))
                .model();
    }

    @Test
    void theStashIsAsTallAsItsRowsInABorder() {
        // 100 rows leave 100 - 3 - 75 = 22 for the stash.
        assertEquals(Option.of(4), LeftColumn.stashHeight(withStashes(2), Option.of(100)));
    }

    @Test
    void withoutStashesTheStashIsABlankLineInABorder() {
        assertEquals(Option.of(3), LeftColumn.stashHeight(withStashes(0), Option.of(100)));
    }

    @Test
    void theStashTakesAtMostWhatTheOtherPanesLeave() {
        // 50 rows leave 50 - 3 - (13 + 10 + 15) = 9 for the stash.
        assertEquals(Option.of(9), LeftColumn.stashHeight(withStashes(30), Option.of(50)));
    }

    @Test
    void theStashTakesAtMostWhatTheDraggedPanesLeave() {
        Model dragged = Update.update(withStashes(30), new LayoutMsg.PaneResized(Screen.REPOSITORY, 2, 95))
                .model();

        // The commits leave the stash its least share, 10 percent: 100 rows leave 100 - 3 - (25 + 20 + 45) = 7.
        assertEquals(Option.of(7), LeftColumn.stashHeight(dragged, Option.of(100)));
    }

    @Test
    void theStashIsAtLeastAnEmptyPaneInATinyColumn() {
        assertEquals(Option.of(3), LeftColumn.stashHeight(withStashes(5), Option.of(10)));
    }

    @Test
    void eachPaneTakesItsShareOfTheColumnAndTheLastWhatIsLeft() {
        assertEquals(List.of(25, 20, 30, 22), LeftColumn.paneRows(List.of(25, 20, 30), 100));
        assertEquals(List.of(14, 14, 13), LeftColumn.paneRows(List.of(32, 32), 44));
        assertEquals(List.of(1, 0), LeftColumn.paneRows(List.of(50), 2));
        // The borders are rounded, at 11.1 and 22.2 rows, not the panes, of 11.1 rows each.
        assertEquals(List.of(11, 11, 12), LeftColumn.paneRows(List.of(30, 30), 37));
    }

    @Test
    void theStashHasNoHeightOfItsOwnWhenTheColumnIsNotKnown() {
        assertEquals(Option.none(), LeftColumn.stashHeight(withStashes(2), Option.none()));
    }
}
