package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
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
        // 50 rows leave 50 - 3 - 37 = 10 for the stash.
        assertEquals(Option.of(10), LeftColumn.stashHeight(withStashes(30), Option.of(50)));
    }

    @Test
    void theStashIsAtLeastAnEmptyPaneInATinyColumn() {
        assertEquals(Option.of(3), LeftColumn.stashHeight(withStashes(5), Option.of(10)));
    }

    @Test
    void theStashHasNoHeightOfItsOwnWhenTheColumnIsNotKnown() {
        assertEquals(Option.none(), LeftColumn.stashHeight(withStashes(2), Option.none()));
    }
}
