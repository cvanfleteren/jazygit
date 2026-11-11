package net.vanfleteren.jazygit.state.view;

import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.diff.Changes;
import org.junit.jupiter.api.Test;

/**
 * Filtering the files of the files pane (ADR 105): typing the filter, keeping it, putting the one from before back,
 * and clearing it, for the tree the pane shows.
 */
class FileFilterUpdateTest {

    private static Model sent(Model model, FileFilterMsg... msgs) {
        return List.of(msgs)
                .foldLeft(model, (next, msg) -> Update.update(next, msg).model());
    }

    private static Option<FileFilter> filter(Model model) {
        return model.repoView().filesFilter();
    }

    private static Model withCommitFiles(Model model) {
        return model.mapRepoView(repoView -> repoView.withCommitFiles(Option.of(CommitFilesView.of(
                new Changes.OfCommit(new Sha("aaaa")), Scopes.COMMITS.pane(), Scopes.FILES.pane()))));
    }

    @Test
    void openingTheLineStartsItEmptyAndTypingFiltersAtOnce() {
        Model opened = sent(loaded(), new FileFilterMsg.Opened());
        assertEquals(Option.of(new FileFilter("", Option.of(""))), filter(opened));
        assertEquals(Option.none(), FileFilter.shown(filter(opened)));

        Model typed = sent(opened, new FileFilterMsg.Edited("test"));
        assertEquals(Option.of("test"), FileFilter.shown(filter(typed)));
        assertTrue(filter(typed).get().typing());
    }

    @Test
    void enterKeepsTheFilterAndClosesTheLine() {
        Model kept = sent(
                loaded(), new FileFilterMsg.Opened(), new FileFilterMsg.Edited("test"), new FileFilterMsg.Submitted());

        assertEquals(Option.of(new FileFilter("test", Option.none())), filter(kept));
    }

    @Test
    void keepingAnEmptyLineFiltersNothing() {
        Model kept = sent(loaded(), new FileFilterMsg.Opened(), new FileFilterMsg.Submitted());

        assertEquals(Option.none(), filter(kept));
    }

    @Test
    void editingDoesNothingWhileTheLineIsClosed() {
        Model kept = sent(
                loaded(), new FileFilterMsg.Opened(), new FileFilterMsg.Edited("test"), new FileFilterMsg.Submitted());

        assertEquals(kept, sent(kept, new FileFilterMsg.Edited("other")));
        assertEquals(loaded(), sent(loaded(), new FileFilterMsg.Edited("other")));
    }

    @Test
    void escapeInTheLinePutsBackTheFilterFromBefore() {
        Model none = sent(loaded(), new FileFilterMsg.Opened(), new FileFilterMsg.Edited("te"));
        assertEquals(Option.none(), filter(sent(none, new FileFilterMsg.Cancelled())));

        Model kept = sent(
                loaded(), new FileFilterMsg.Opened(), new FileFilterMsg.Edited("test"), new FileFilterMsg.Submitted());
        Model reopened = sent(kept, new FileFilterMsg.Opened());
        assertEquals(Option.of(new FileFilter("test", Option.of("test"))), filter(reopened));

        Model changed = sent(reopened, new FileFilterMsg.Edited("main"), new FileFilterMsg.Cancelled());
        assertEquals(Option.of(new FileFilter("test", Option.none())), filter(changed));
    }

    @Test
    void clearingShowsAllFilesAgain() {
        Model kept = sent(
                loaded(), new FileFilterMsg.Opened(), new FileFilterMsg.Edited("test"), new FileFilterMsg.Submitted());

        assertEquals(Option.none(), filter(sent(kept, new FileFilterMsg.Cleared())));
    }

    @Test
    void theFilesOfOtherChangesHaveAFilterOfTheirOwn() {
        Model working = sent(
                loaded(), new FileFilterMsg.Opened(), new FileFilterMsg.Edited("test"), new FileFilterMsg.Submitted());
        Model open = withCommitFiles(working);
        assertEquals(Option.none(), FileFilterUpdate.shown(open));

        Model filtered =
                sent(open, new FileFilterMsg.Opened(), new FileFilterMsg.Edited("src"), new FileFilterMsg.Submitted());
        assertEquals(
                Option.of(new FileFilter("src", Option.none())),
                filtered.repoView().commitFiles().flatMap(CommitFilesView::filter));
        assertEquals(Option.of("test"), FileFilterUpdate.workingTree(filtered));

        // Closing the files of the commit drops their filter; that of the working tree is still there.
        Model closed = filtered.mapRepoView(repoView -> repoView.withCommitFiles(Option.none()));
        assertEquals(Option.of(new FileFilter("test", Option.none())), FileFilterUpdate.shown(closed));
        assertEquals(Option.none(), FileFilterUpdate.shown(withCommitFiles(closed)));
    }
}
