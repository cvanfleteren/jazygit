package net.vanfleteren.jazygit.kernel.content;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.gh.model.ReviewThread.Side;
import net.vanfleteren.jazygit.core.git.model.DiffText;
import net.vanfleteren.jazygit.kernel.content.diff.ShownRemarks;
import net.vanfleteren.jazygit.kernel.settings.DiffLook;
import net.vanfleteren.jazygit.kernel.settings.DiffStyle;
import net.vanfleteren.jazygit.kernel.state.diff.Anchor;
import net.vanfleteren.jazygit.kernel.state.diff.Collapsed;
import net.vanfleteren.jazygit.kernel.state.diff.DiffStops;
import net.vanfleteren.jazygit.kernel.ui.content.ShownChanges;
import net.vanfleteren.jazygit.kernel.ui.content.ShownDiff;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;
import org.junit.jupiter.api.Test;

class DiffBodyTest {

    private static final String PAIRS = """
            diff --git a/a.txt b/a.txt
            index 1111111..2222222 100644
            --- a/a.txt
            +++ b/a.txt
            @@ -1,2 +1,2 @@
            -int count = 1;
            -int total = 2;
            +int count = 42;
            +int total = 43;
            diff --git a/b.txt b/b.txt
            index 1111111..2222222 100644
            --- a/b.txt
            +++ b/b.txt
            @@ -1,3 +1,3 @@
             one
            -two
            +TWO
             three
            """;

    private static final ShownRemarks NO_REMARKS = new ShownRemarks(
            List.empty(),
            Instant.parse("2026-10-30T12:00:00Z"),
            Option.none(),
            true,
            Collapsed.NONE,
            HashSet.empty(),
            HashSet.empty());

    private static ShownChanges changes(DiffStyle style) {
        return new ShownChanges(new ShownDiff(DiffText.of(PAIRS), Option.none()), new DiffLook(style, true, true));
    }

    @Test
    void theHunkOfARangeIsFoundInTheDiffWithoutDrawingIt() {
        DiffBody body = new DiffBody(changes(DiffStyle.RICH));

        assertEquals(Option.of(new DiffStops.HunkAt(0, 0)), body.markedHunk(new Anchor.OnLine("a.txt", Side.LEFT, 2)));
        assertEquals(Option.of(new DiffStops.HunkAt(1, 0)), body.markedHunk(new Anchor.OnLine("b.txt", Side.RIGHT, 2)));
        assertEquals(Option.none(), body.markedHunk(new Anchor.OnLine("c.txt", Side.RIGHT, 2)));
    }

    @Test
    void onlyADiffWithReviewRemarksTakesTheKeysThatCommentOnIt() {
        assertEquals(
                Scopes.PULL_REQUEST_DIFF, new DiffBody(changes(DiffStyle.RICH), Option.of(NO_REMARKS)).cursorKeys());
        assertEquals(Scopes.DIFF, new DiffBody(changes(DiffStyle.RICH)).cursorKeys());
    }
}
