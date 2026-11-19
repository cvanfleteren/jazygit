package net.vanfleteren.jazygit.features.changes.diff;

import static net.vanfleteren.jazygit.kernel.state.TestModels.DIRTY;
import static net.vanfleteren.jazygit.kernel.state.TestModels.loaded;
import static net.vanfleteren.jazygit.kernel.state.TestModels.refused;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.BranchName;
import net.vanfleteren.jazygit.core.git.model.BranchRef;
import net.vanfleteren.jazygit.core.git.model.DiffContext;
import net.vanfleteren.jazygit.core.git.model.DiffOptions;
import net.vanfleteren.jazygit.core.git.model.DiffText;
import net.vanfleteren.jazygit.core.git.model.Diffs;
import net.vanfleteren.jazygit.core.git.model.FileEntry;
import net.vanfleteren.jazygit.core.git.model.RemoteBranch;
import net.vanfleteren.jazygit.core.git.model.Sha;
import net.vanfleteren.jazygit.features.changes.commit.amend.AmendPopup;
import net.vanfleteren.jazygit.features.changes.commit.amend.AmendTarget;
import net.vanfleteren.jazygit.kernel.settings.DiffStyle;
import net.vanfleteren.jazygit.kernel.state.Loadable.Loaded;
import net.vanfleteren.jazygit.kernel.state.Loadable.Loading;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.load.LoadCommitDetail;
import net.vanfleteren.jazygit.kernel.state.load.LoadFileDiff;
import net.vanfleteren.jazygit.kernel.state.load.LoadMsg;
import net.vanfleteren.jazygit.kernel.state.load.LoadStashDetail;
import net.vanfleteren.jazygit.kernel.state.load.SelectionMsg;
import net.vanfleteren.jazygit.kernel.state.preferences.SaveSettings;
import net.vanfleteren.jazygit.kernel.state.view.DivergenceView;
import net.vanfleteren.jazygit.kernel.state.view.Preview;
import net.vanfleteren.jazygit.kernel.ui.widgets.PickDialog;
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
                List.of(new LoadFileDiff(DIRTY.files(), IGNORING), new LoadCommitDetail(SHA, IGNORING)), next.cmds());
        Preview<List<FileEntry>, Diffs> diff =
                next.model().repoView().preview().fileDiff().get();
        assertEquals(new Loading<>(), diff.value());
        assertEquals(new Loaded<>(new Diffs("", "+a ")), diff.shown().value());
        assertEquals(
                new Loaded<>(DiffText.of("changes")),
                next.model().repoView().preview().commitDetail().get().shown().value());

        assertEquals(
                DiffOptions.DEFAULT,
                Update.update(next.model(), new DiffOptionsMsg.WhitespaceToggled())
                        .model()
                        .preferences()
                        .diffOptions());
    }

    @Test
    void wideningTheContextReadsTheShownDiffsAgainWithMoreLines() {
        DiffOptions wider = DiffOptions.DEFAULT.withContext(new DiffContext(5));

        Next next = Update.update(shown(), new DiffOptionsMsg.ContextWidened());

        assertEquals(wider, next.model().preferences().diffOptions());
        assertEquals(List.of(new LoadFileDiff(DIRTY.files(), wider), new LoadCommitDetail(SHA, wider)), next.cmds());
        assertEquals(
                DiffOptions.DEFAULT,
                Update.update(next.model(), new DiffOptionsMsg.ContextNarrowed())
                        .model()
                        .preferences()
                        .diffOptions());
    }

    @Test
    void steppingPastTheLastAmountReadsNothingAgain() {
        Model narrowest = shown().mapPreferences(preferences -> preferences.withContext(new DiffContext(0)));
        Model widest = shown().mapPreferences(preferences -> preferences.withContext(DiffContext.WHOLE_FILE));

        Next narrowed = Update.update(narrowest, new DiffOptionsMsg.ContextNarrowed());
        Next widened = Update.update(widest, new DiffOptionsMsg.ContextWidened());

        assertSame(narrowest, narrowed.model());
        assertEquals(List.of(), narrowed.cmds());
        assertSame(widest, widened.model());
        assertEquals(List.of(), widened.cmds());
    }

    @Test
    void togglingWordsKeepsItForTheNextSessionWithoutReadingTheDiffsAgain() {
        Model model = shown().mapPreferences(preferences ->
                preferences.withSettings(preferences.settings().withDiffStyle(DiffStyle.RICH)));

        Next next = Update.update(model, new DiffOptionsMsg.WordsToggled());

        assertFalse(next.model().preferences().diffLook().words());
        assertEquals(
                List.of(new SaveSettings(
                        model.preferences().settings(),
                        model.preferences().settings().withDiffWords(false))),
                next.cmds());
        assertEquals(model.repoView(), next.model().repoView());
        assertTrue(Update.update(next.model(), new DiffOptionsMsg.WordsToggled())
                .model()
                .preferences()
                .diffLook()
                .words());
    }

    @Test
    void whatWasReadBeforeTheToggleIsDropped() {
        Model toggled =
                Update.update(shown(), new DiffOptionsMsg.WhitespaceToggled()).model();

        Model stale = Update.update(
                        toggled, new LoadMsg.FileDiffLoaded(DIRTY.files(), DiffOptions.DEFAULT, new Diffs("", "+b ")))
                .model();
        assertEquals(
                new Loading<>(), stale.repoView().preview().fileDiff().get().value());

        Model read = Update.update(toggled, new LoadMsg.FileDiffLoaded(DIRTY.files(), IGNORING, new Diffs("", "")))
                .model();
        assertEquals(
                Option.of(new Preview<>(DIRTY.files(), new Loaded<>(new Diffs("", "")))),
                read.repoView().preview().fileDiff());
    }

    @Test
    void failuresAndCommitChangesReadBeforeTheToggleAreDropped() {
        Model toggled =
                Update.update(shown(), new DiffOptionsMsg.WhitespaceToggled()).model();

        assertDropped(
                toggled, new LoadMsg.Failed(new LoadFileDiff(DIRTY.files(), DiffOptions.DEFAULT), refused("boom")));
        assertDropped(toggled, new LoadMsg.Failed(new LoadCommitDetail(SHA, DiffOptions.DEFAULT), refused("boom")));
        assertDropped(toggled, new LoadMsg.CommitDetailLoaded(SHA, DiffOptions.DEFAULT, DiffText.of("stale")));
    }

    @Test
    void whatWasReadForTheCommitOfADivergenceBeforeTheToggleIsDropped() {
        Model model = loaded().mapRepoView(repoView ->
                repoView.mapBranches(branches -> branches.withDivergence(Option.of(DivergenceView.loading(
                        new BranchName("main"),
                        new BranchRef.Remote(new RemoteBranch("origin", new BranchName("main"))),
                        DivergenceView.Against.UPSTREAM)))));
        model = Update.update(model, new SelectionMsg.DivergenceCommitSelected(SHA))
                .model();
        model = Update.update(model, new LoadMsg.CommitDetailLoaded(SHA, DiffOptions.DEFAULT, DiffText.of("changes")))
                .model();
        Model toggled =
                Update.update(model, new DiffOptionsMsg.WhitespaceToggled()).model();

        assertDropped(toggled, new LoadMsg.Failed(new LoadCommitDetail(SHA, DiffOptions.DEFAULT), refused("boom")));
        assertDropped(toggled, new LoadMsg.CommitDetailLoaded(SHA, DiffOptions.DEFAULT, DiffText.of("stale")));
    }

    @Test
    void highlightingAfterTheToggleReadsWithTheNewOptions() {
        Model toggled =
                Update.update(loaded(), new DiffOptionsMsg.WhitespaceToggled()).model();

        assertEquals(
                List.of(new LoadCommitDetail(SHA, IGNORING)),
                Update.update(toggled, new SelectionMsg.CommitSelected(SHA)).cmds());
        assertEquals(
                List.of(new LoadFileDiff(DIRTY.files(), IGNORING)),
                Update.update(toggled, new SelectionMsg.FilesSelected(DIRTY.files()))
                        .cmds());
    }

    @Test
    void togglingWhitespaceReadsTheShownStashAgainAndDropsWhatWasReadBefore() {
        Sha stash = new Sha("5555");
        Model model =
                Update.update(loaded(), new SelectionMsg.StashSelected(stash)).model();
        model = Update.update(model, new LoadMsg.StashDetailLoaded(stash, DiffOptions.DEFAULT, DiffText.of("changes")))
                .model();

        Next next = Update.update(model, new DiffOptionsMsg.WhitespaceToggled());

        assertEquals(List.of(new LoadStashDetail(stash, IGNORING)), next.cmds());
        Preview<Sha, DiffText> detail =
                next.model().repoView().preview().stashDetail().get();
        assertEquals(new Loading<>(), detail.value());
        assertEquals(new Loaded<>(DiffText.of("changes")), detail.shown().value());
        assertEquals(
                new Loading<>(),
                Update.update(
                                next.model(),
                                new LoadMsg.StashDetailLoaded(stash, DiffOptions.DEFAULT, DiffText.of("stale")))
                        .model()
                        .repoView()
                        .preview()
                        .stashDetail()
                        .get()
                        .value());

        assertDropped(
                next.model(), new LoadMsg.Failed(new LoadStashDetail(stash, DiffOptions.DEFAULT), refused("boom")));
    }

    @Test
    void ctrlDOpensThePopupOfTheOptionsWhichShowsEachChangeAtOnce() {
        Model open = Update.update(shown(), new DiffOptionsMsg.Requested()).model();
        assertEquals(DiffStyle.PLAIN, open.popup(DiffOptionsPopup.class).get().style());

        Model rich = Update.update(open, new DiffOptionsMsg.StyleChosen(DiffStyle.RICH))
                .model();
        Model whitespace =
                Update.update(rich, new DiffOptionsMsg.WhitespaceToggled()).model();
        Model wider =
                Update.update(whitespace, new DiffOptionsMsg.ContextWidened()).model();
        Model words = Update.update(wider, new DiffOptionsMsg.WordsToggled()).model();
        Model split = Update.update(words, new DiffOptionsMsg.SplitToggled()).model();

        assertEquals(
                new DiffOptionsPopup(
                        DiffStyle.SPLIT,
                        !open.preferences().settings().diffWords(),
                        true,
                        DiffContext.DEFAULT.wider(),
                        open.preferences().diffFilters()),
                split.popup(DiffOptionsPopup.class).get());
    }

    @Test
    void theChangedWordsCannotBeSwitchedInThePlainStyle() {
        Model plain = Update.update(shown(), new DiffOptionsMsg.StyleChosen(DiffStyle.PLAIN))
                .model();

        Next next = Update.update(plain, new DiffOptionsMsg.WordsToggled());

        assertSame(plain, next.model());
        assertEquals(List.of(), next.cmds());
    }

    @Test
    void theRowsStepTheStyleRoundAndTheContextAndSendSpaceForTheFiltersSwitch() {
        Model open = Update.update(shown(), new DiffOptionsMsg.Requested()).model();
        List<PickDialog.Row<Msg>> rows =
                DiffOptionsDialog.rows(open.popup(DiffOptionsPopup.class).get());

        assertEquals(new DiffOptionsMsg.StyleChosen(DiffStyle.RICH), rows.head().value());
        assertEquals(
                Option.of(new PickDialog.Steps<Msg>(
                        new DiffOptionsMsg.StyleChosen(DiffStyle.SPLIT),
                        new DiffOptionsMsg.StyleChosen(DiffStyle.RICH))),
                rows.head().steps());
        assertEquals(
                Option.of(new PickDialog.Steps<Msg>(
                        new DiffOptionsMsg.ContextNarrowed(), new DiffOptionsMsg.ContextWidened())),
                rows.get(1).steps());
        PickDialog.Row<Msg> filters = rows.get(DiffOptionsDialog.FILTERS_ROW);
        assertEquals(new DiffFiltersMsg.Requested(), filters.value());
        assertEquals(Option.of(new DiffFiltersMsg.AllToggled()), filters.spaced());
    }

    @Test
    void theToggleDoesNothingWhileADialogIsOpen() {
        Model model = shown().openPopup(new AmendPopup(new AmendTarget.LastCommit()));

        Next next = Update.update(model, new DiffOptionsMsg.WhitespaceToggled());

        assertSame(model, next.model());
        assertEquals(List.of(), next.cmds());
    }

    /**
     * Asserts that {@code msg}, read with options the model no longer has, changes nothing but that its load is no
     * longer running.
     */
    private static void assertDropped(Model model, LoadMsg msg) {
        Next next = TestModels.done(TestModels.running(model, msg.cmd()), msg.cmd(), msg);

        assertEquals(model.repoView(), next.model().repoView());
        assertFalse(next.model().jobs().runs(msg.cmd()));
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
