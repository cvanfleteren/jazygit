package net.vanfleteren.jazygit.feature.copy;

import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.ArrayList;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.feature.diff.ExpandMsg;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Side;
import net.vanfleteren.jazygit.git.StubGit;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.settings.RecentRepositories;
import net.vanfleteren.jazygit.settings.SettingsStore;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Repositories;
import net.vanfleteren.jazygit.state.Terminal;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.diff.Anchor;
import net.vanfleteren.jazygit.state.diff.DiffCursorAt;
import net.vanfleteren.jazygit.state.diff.Scrolled;
import net.vanfleteren.jazygit.state.diff.ScrolledContent;
import net.vanfleteren.jazygit.state.load.LoadMsg;
import net.vanfleteren.jazygit.state.load.ReadWholeFile;
import net.vanfleteren.jazygit.state.operation.Notice;
import org.junit.jupiter.api.Test;

class CopyUpdateTest {

    private static final String AREA = "stash-changes";
    private static final String HEADER = "diff --git a/a.txt b/a.txt\n--- a/a.txt\n+++ b/a.txt\n";
    /**
     * Line 10 of a file of 30 changed, as git shows it with three lines of context.
     */
    private static final DiffText CHANGES = DiffText.of(HEADER + "@@ -7,7 +7,7 @@\n"
            + List.rangeClosed(7, 13).map(CopyUpdateTest::line).mkString());
    /**
     * The same change, read with the whole file as context.
     */
    private static final DiffText WHOLE = DiffText.of(HEADER + "@@ -1,30 +1,30 @@\n"
            + List.rangeClosed(1, 30).map(CopyUpdateTest::line).mkString());

    private static final ScrolledContent CONTENT = new ScrolledContent.StashChanges(new Sha("5555"), CHANGES);

    @Test
    void theLineOfTheCursorIsCopiedWithoutItsMarker() {
        Next next = Update.update(loaded(), new CopyMsg.Asked(at(right(10), Option.none())));

        assertEquals(List.of(new CopyCmd("changed", new Notice.Copied(1))), next.cmds());
    }

    @Test
    void aRangeIsCopiedInTheOrderOfTheDiffFromWhicheverEndComesFirst() {
        CopyCmd down = new CopyCmd("line9\nline10\nchanged\nline11", new Notice.Copied(4));

        assertEquals(
                List.of(down),
                Update.update(loaded(), new CopyMsg.Asked(at(right(11), Option.of(right(9)))))
                        .cmds());
        assertEquals(
                List.of(down),
                Update.update(loaded(), new CopyMsg.Asked(at(right(9), Option.of(right(11)))))
                        .cmds());
    }

    @Test
    void onTheBarOfAFileItsPathIsCopied() {
        Next next = Update.update(loaded(), new CopyMsg.Asked(at(new Anchor.File("a.txt"), Option.none())));

        assertEquals(List.of(new CopyCmd("a.txt", new Notice.PathCopied("a.txt"))), next.cmds());
    }

    @Test
    void anExpandedLineIsCopiedToo() {
        Anchor.Gap above = new Anchor.Gap("a.txt", 1, Option.of(7));
        Next asked = Update.update(loaded(), new ExpandMsg.Asked(at(above, Option.none()), ExpandMsg.Toward.ALL));
        ReadWholeFile read = (ReadWholeFile) asked.cmds().head();
        Model expanded = TestModels.done(
                        TestModels.running(asked.model(), read), read, new LoadMsg.WholeFileRead(read, WHOLE))
                .model();

        Next next = Update.update(expanded, new CopyMsg.Asked(at(right(2), Option.of(right(3)))));

        assertEquals(List.of(new CopyCmd("line2\nline3", new Notice.Copied(2))), next.cmds());
    }

    @Test
    void elsewhereNothingIsCopied() {
        Model model = loaded();

        Next onLead = Update.update(model, new CopyMsg.Asked(at(new Anchor.Lead(), Option.none())));
        Next onMarker =
                Update.update(model, new CopyMsg.Asked(at(new Anchor.Gap("a.txt", 1, Option.of(7)), Option.none())));
        Next offTheDiff = Update.update(model, new CopyMsg.Asked(at(right(99), Option.none())));

        assertEquals(List.of(), onLead.cmds().appendAll(onMarker.cmds()).appendAll(offTheDiff.cmds()));
        assertSame(model, onLead.model());
    }

    @Test
    void theCommandLogSaysWhatWasCopied() {
        Next next = Update.update(loaded(), new CopyMsg.Done(new Notice.Copied(3)));

        assertEquals(Option.of(new Notice.Copied(3)), next.model().outcome().notice());
    }

    @Test
    void theTerminalIsAskedToCopyTheText() {
        java.util.List<String> copied = new ArrayList<>();
        Terminal terminal = new Terminal() {
            @Override
            public <T> T handOver(Supplier<T> program) {
                return program.get();
            }

            @Override
            public Copy copy(String text) {
                copied.add(text);
                return Copy.ASKED;
            }
        };

        var done = new CopyCmd("line9\nline10", new Notice.Copied(2)).run(env(terminal));

        assertEquals(java.util.List.of("line9\nline10"), copied);
        assertEquals(new CopyMsg.Done(new Notice.Copied(2)), done);
    }

    @Test
    void withoutATerminalOfItsOwnNothingIsCopied() {
        var done = new CopyCmd("line9", new Notice.Copied(1)).run(env(Terminal.NONE));

        assertEquals(new CopyMsg.Done(new Notice.NotCopied()), done);
    }

    @Test
    void whenTheTerminalCannotBeWrittenToTheCopyFailed() {
        Terminal broken = new Terminal() {
            @Override
            public <T> T handOver(Supplier<T> program) {
                return program.get();
            }

            @Override
            public Copy copy(String text) {
                return Copy.FAILED;
            }
        };
        CopyCmd copy = new CopyCmd("line9", new Notice.Copied(1));

        assertEquals(new CopyMsg.Done(new Notice.CopyFailed()), copy.run(env(broken)));
        assertEquals(
                new CopyMsg.Done(new Notice.CopyFailed()),
                copy.failed(new Failure.Bug(new IllegalStateException("boom"))));
    }

    @Test
    void aRangeWhoseMarkIsNoLongerInTheDiffCopiesNothing() {
        Model model = loaded();

        Next next = Update.update(model, new CopyMsg.Asked(at(right(10), Option.of(right(99)))));

        assertEquals(List.of(), next.cmds());
        assertSame(model, next.model());
    }

    /**
     * The cursor on {@code cursor} in the changes of the stash, with a range open from {@code mark}.
     */
    private static DiffCursorAt at(Anchor cursor, Option<Anchor> mark) {
        return new DiffCursorAt(
                AREA,
                new Scrolled(CONTENT, 0, 0, Option.of(cursor), mark),
                cursor,
                Option.none(),
                Option.none(),
                Option.none());
    }

    private static Anchor.OnLine right(int number) {
        return new Anchor.OnLine("a.txt", Side.RIGHT, number);
    }

    private static Env env(Terminal terminal) {
        return new Env(
                StubGit.of(),
                StubGitHub.empty(),
                SettingsStore.NONE,
                RecentRepositories.NONE,
                Repositories.NONE,
                terminal);
    }

    private static String line(int number) {
        return number == 10 ? "-line10\n+changed\n" : " line" + number + "\n";
    }
}
