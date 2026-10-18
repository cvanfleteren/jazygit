package net.vanfleteren.jazygit.feature.diff;

import static net.vanfleteren.jazygit.state.TestModels.DIRTY;
import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.commit.amend.AmendPopup;
import net.vanfleteren.jazygit.git.model.DiffOptions;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.Diffs;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.LoadMsg;
import net.vanfleteren.jazygit.state.Loadable.Loaded;
import net.vanfleteren.jazygit.state.Loadable.Loading;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Preview;
import net.vanfleteren.jazygit.state.SelectionMsg;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

class DiffOptionsUpdateTest {

    private static final DiffOptions IGNORING = DiffOptions.DEFAULT.withIgnoreWhitespace(true);
    private static final Sha SHA = new Sha("aaaa");

    @Test
    void togglingWhitespaceReadsTheShownDiffsAgainKeepingThemVisible() {
        Model model = shown();

        Next next = Update.update(model, new DiffOptionsMsg.WhitespaceToggled());

        assertEquals(IGNORING, next.model().preferences().diffOptions());
        assertEquals(
                List.of(new Cmd.LoadFileDiff(DIRTY.files(), IGNORING), new Cmd.LoadCommitDetail(SHA, IGNORING)),
                next.cmds());
        Preview<List<FileEntry>, Diffs> diff =
                next.model().repoView().fileDiff().get();
        assertEquals(new Loading<>(), diff.value());
        assertEquals(new Loaded<>(new Diffs("", "+a ")), diff.shown().value());
        assertEquals(
                new Loaded<>(DiffText.of("changes")),
                next.model().repoView().commitDetail().get().shown().value());

        assertEquals(
                DiffOptions.DEFAULT,
                Update.update(next.model(), new DiffOptionsMsg.WhitespaceToggled())
                        .model()
                        .preferences()
                        .diffOptions());
    }

    @Test
    void whatWasReadBeforeTheToggleIsDropped() {
        Model toggled =
                Update.update(shown(), new DiffOptionsMsg.WhitespaceToggled()).model();

        Model stale = Update.update(
                        toggled, new LoadMsg.FileDiffLoaded(DIRTY.files(), DiffOptions.DEFAULT, new Diffs("", "+b ")))
                .model();
        assertEquals(new Loading<>(), stale.repoView().fileDiff().get().value());

        Model read = Update.update(toggled, new LoadMsg.FileDiffLoaded(DIRTY.files(), IGNORING, new Diffs("", "")))
                .model();
        assertEquals(
                Option.of(new Preview<>(DIRTY.files(), new Loaded<>(new Diffs("", "")))),
                read.repoView().fileDiff());
    }

    @Test
    void highlightingAfterTheToggleReadsWithTheNewOptions() {
        Model toggled =
                Update.update(loaded(), new DiffOptionsMsg.WhitespaceToggled()).model();

        assertEquals(
                List.of(new Cmd.LoadCommitDetail(SHA, IGNORING)),
                Update.update(toggled, new SelectionMsg.CommitSelected(SHA)).cmds());
        assertEquals(
                List.of(new Cmd.LoadFileDiff(DIRTY.files(), IGNORING)),
                Update.update(toggled, new SelectionMsg.FilesSelected(DIRTY.files()))
                        .cmds());
    }

    @Test
    void theToggleDoesNothingWhileADialogIsOpen() {
        Model model = shown().openPopup(new AmendPopup());

        Next next = Update.update(model, new DiffOptionsMsg.WhitespaceToggled());

        assertSame(model, next.model());
        assertEquals(List.of(), next.cmds());
    }

    /**
     * A model that shows the diff of the files and the changes of a commit.
     */
    private static Model shown() {
        Model model = Update.update(loaded(), new SelectionMsg.FilesSelected(DIRTY.files()))
                .model();
        model = Update.update(model, new SelectionMsg.CommitSelected(SHA)).model();
        model = Update.update(
                        model, new LoadMsg.FileDiffLoaded(DIRTY.files(), DiffOptions.DEFAULT, new Diffs("", "+a ")))
                .model();
        return Update.update(model, new LoadMsg.CommitDetailLoaded(SHA, DiffOptions.DEFAULT, DiffText.of("changes")))
                .model();
    }
}
