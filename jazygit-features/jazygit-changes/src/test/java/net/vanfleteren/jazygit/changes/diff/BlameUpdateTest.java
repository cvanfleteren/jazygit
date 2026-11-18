package net.vanfleteren.jazygit.changes.diff;

import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Side;
import net.vanfleteren.jazygit.git.model.Blame;
import net.vanfleteren.jazygit.git.model.BlameAt;
import net.vanfleteren.jazygit.git.model.BlameOptions;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.FileVersion;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.AreaId;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.diff.Anchor;
import net.vanfleteren.jazygit.state.diff.DiffCursorAt;
import net.vanfleteren.jazygit.state.diff.Scrolled;
import net.vanfleteren.jazygit.state.diff.ScrolledContent;
import net.vanfleteren.jazygit.state.diff.Version;
import net.vanfleteren.jazygit.state.load.LoadMsg;
import net.vanfleteren.jazygit.state.load.ReadBlame;
import net.vanfleteren.jazygit.state.load.ReadVersion;
import org.junit.jupiter.api.Test;

/**
 * Blame on a version of a file (ADR 147): {@code b} turns it on and off for the area, and what shows blamed is read
 * once, as it is shown.
 */
class BlameUpdateTest {

    private static final AreaId AREA = new AreaId("stash-changes");
    private static final Sha STASH = new Sha("5555");
    private static final String HEADER = "diff --git a/a.txt b/a.txt\n--- a/a.txt\n+++ b/a.txt\n";
    private static final DiffText CHANGES = DiffText.of(HEADER + "@@ -1,3 +1,3 @@\n one\n-two\n+Two\n three\n");
    private static final DiffText WHOLE = CHANGES;
    private static final ScrolledContent CONTENT = new ScrolledContent.StashChanges(STASH, CHANGES);
    private static final Anchor.OnLine ADDED = new Anchor.OnLine("a.txt", Side.RIGHT, 2);
    private static final Blame BLAME = Blame.uncommitted(3, "a.txt");

    @Test
    void bOnTheNewVersionReadsItsBlameInTheStash() {
        Next blamed = Update.update(newVersion(), new VersionMsg.BlameToggled(at()));

        ReadBlame read = onlyRead(blamed.cmds());
        assertEquals(FileVersion.NEW, read.version());
        assertEquals(List.of(new BlameAt.InCommit(STASH, "a.txt")), read.at());
        assertEquals(BlameOptions.DEFAULT, read.options());
        assertEquals(Option.of(Loadable.loading()), version(blamed.model()).flatMap(Version::blame));
    }

    @Test
    void theBlameReadShowsAndBAgainStopsBlaming() {
        Model read = blameRead(Update.update(newVersion(), new VersionMsg.BlameToggled(at())));
        assertEquals(Option.of(new Loadable.Loaded<>(BLAME)), version(read).flatMap(Version::blame));

        Next off = Update.update(read, new VersionMsg.BlameToggled(at()));

        assertEquals(List.of(), off.cmds());
        assertEquals(Option.none(), version(off.model()).flatMap(Version::blame));
        // Turned on again, what was read shows at once.
        Next on = Update.update(off.model(), new VersionMsg.BlameToggled(at()));
        assertEquals(List.of(), on.cmds());
        assertEquals(
                Option.of(new Loadable.Loaded<>(BLAME)), version(on.model()).flatMap(Version::blame));
    }

    @Test
    void theOriginalShowsBlamedTooReadAtTheParentOfTheStash() {
        Model read = blameRead(Update.update(newVersion(), new VersionMsg.BlameToggled(at())));

        Next original = Update.update(read, new VersionMsg.Cycled(at()));

        ReadBlame blame = onlyRead(original.cmds());
        assertEquals(FileVersion.ORIGINAL, blame.version());
        assertEquals(List.of(new BlameAt.InCommit(STASH, 1, "a.txt")), blame.at());
        // Back to the new version, through the diff, its blame shows without being read again.
        Model diff =
                Update.update(original.model(), new VersionMsg.Cycled(at())).model();
        Next back = Update.update(diff, new VersionMsg.Cycled(at()));
        assertEquals(List.of(), back.cmds().filter(ReadBlame.class::isInstance));
        assertEquals(
                Option.of(new Loadable.Loaded<>(BLAME)), version(back.model()).flatMap(Version::blame));
    }

    @Test
    void bOnTheDiffDoesNothing() {
        Next next = Update.update(loaded(), new VersionMsg.BlameToggled(at()));

        assertEquals(List.of(), next.cmds());
        assertEquals(Option.none(), version(next.model()));
    }

    @Test
    void aVersionStillReadIsBlamedOnceItIsRead() {
        Next asked = Update.update(loaded(), new VersionMsg.Cycled(at()));
        Next blamed = Update.update(asked.model(), new VersionMsg.BlameToggled(at()));
        assertEquals(List.of(), blamed.cmds());

        ReadVersion read = (ReadVersion) asked.cmds().head();
        Next shown =
                TestModels.done(TestModels.running(blamed.model(), read), read, new LoadMsg.VersionRead(read, WHOLE));

        assertEquals(FileVersion.NEW, onlyRead(shown.cmds()).version());
    }

    @Test
    void aBlameThatFailedIsReadAgainOnTheNextB() {
        Next blamed = Update.update(newVersion(), new VersionMsg.BlameToggled(at()));
        ReadBlame read = onlyRead(blamed.cmds());
        Model failed = TestModels.done(
                        TestModels.running(blamed.model(), read),
                        read,
                        new LoadMsg.Failed(read, TestModels.refused("fatal: no such path")))
                .model();
        assertTrue(version(failed).flatMap(Version::blame).exists(Loadable::isFailed));

        Next again = Update.update(failed, new VersionMsg.BlameToggled(at()));

        assertEquals(read, onlyRead(again.cmds()));
        assertTrue(version(again.model()).exists(Version::blamed));
    }

    @Test
    void ctrlWBlamesAgainIgnoringWhitespaceAndDropsTheBlameReadBefore() {
        Next blamed = Update.update(newVersion(), new VersionMsg.BlameToggled(at()));
        ReadBlame before = onlyRead(blamed.cmds());
        Model running = TestModels.running(blamed.model(), before);

        Next ignoring = Update.update(running, new DiffOptionsMsg.WhitespaceToggled());

        ReadBlame after = onlyRead(ignoring.cmds());
        assertEquals(new BlameOptions(true, Option.none()), after.options());
        Model late = TestModels.done(ignoring.model(), before, new LoadMsg.BlameRead(before, BLAME))
                .model();
        assertEquals(Option.of(Loadable.loading()), version(late).flatMap(Version::blame));
    }

    /**
     * The new version of the file of the stash shown, read whole.
     */
    private static Model newVersion() {
        Next asked = Update.update(loaded(), new VersionMsg.Cycled(at()));
        ReadVersion read = (ReadVersion) asked.cmds().head();
        Model shown = TestModels.done(
                        TestModels.running(asked.model(), read), read, new LoadMsg.VersionRead(read, WHOLE))
                .model();
        assertEquals(Option.of(FileVersion.NEW), version(shown).flatMap(Version::showing));
        return shown;
    }

    /**
     * {@code asked} once the one blame it reads is read.
     */
    private static Model blameRead(Next asked) {
        ReadBlame read = onlyRead(asked.cmds());
        return TestModels.done(TestModels.running(asked.model(), read), read, new LoadMsg.BlameRead(read, BLAME))
                .model();
    }

    private static ReadBlame onlyRead(List<Cmd> cmds) {
        List<Cmd> reads = cmds.filter(ReadBlame.class::isInstance);
        assertEquals(1, reads.size(), cmds.toString());
        return (ReadBlame) reads.head();
    }

    private static DiffCursorAt at() {
        return new DiffCursorAt(
                AREA,
                new Scrolled(CONTENT, 0, 0, Option.of(ADDED), Option.none()),
                ADDED,
                Option.none(),
                Option.none(),
                Option.none());
    }

    private static Option<Version> version(Model model) {
        return model.repoView().versions().get(AREA);
    }
}
