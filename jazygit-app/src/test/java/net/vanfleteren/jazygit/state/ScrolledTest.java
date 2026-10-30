package net.vanfleteren.jazygit.state;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.CommitFile;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.Sha;
import org.junit.jupiter.api.Test;

/**
 * Where the areas of the content panel are scrolled to: kept while an area shows the same content, from the top
 * for anything else.
 */
class ScrolledTest {

    private static final ScrolledContent AB = new ScrolledContent.Diff(DiffText.of("+a\n+b\n"));
    private static final ScrolledContent C = new ScrolledContent.Diff(DiffText.of("+c\n"));

    private final Model scrolled = Update.update(
                    TestModels.loaded(),
                    new Msg.ContentScrolled("diff", new Scrolled(AB, 3, 1, io.vavr.control.Option.none())))
            .model();

    @Test
    void anAreaShowsItsContentFromTheTopUntilItIsScrolled() {
        assertEquals(Scrolled.top(C), TestModels.loaded().scrolled("diff", C));
    }

    @Test
    void anAreaStaysWhereItWasScrolledToWhileItShowsTheSameContent() {
        assertEquals(new Scrolled(AB, 3, 1, io.vavr.control.Option.none()), scrolled.scrolled("diff", AB));
    }

    @Test
    void otherContentStartsAtTheTop() {
        assertEquals(Scrolled.top(C), scrolled.scrolled("diff", C));
    }

    @Test
    void theSameChangesReadAgainKeepTheCursorFromTheTop() {
        Sha sha = new Sha("aaaa");
        List<CommitFile> files = List.of(new CommitFile("a.txt", ChangeType.MODIFIED, Option.none()));
        ScrolledContent before = new ScrolledContent.FileChanges(sha, files, DiffText.of("+a\n"));
        ScrolledContent again = new ScrolledContent.FileChanges(sha, files, DiffText.of("+b\n"));
        ScrolledContent other = new ScrolledContent.FileChanges(new Sha("bbbb"), files, DiffText.of("+b\n"));
        Option<Anchor> cursor = Option.of(new Anchor.File("a.txt"));
        Model at = Update.update(
                        TestModels.loaded(), new Msg.ContentScrolled("diff", new Scrolled(before, 5, 2, cursor)))
                .model();

        assertEquals(new Scrolled(again, 0, 0, cursor), at.scrolled("diff", again));
        assertEquals(Scrolled.top(other), at.scrolled("diff", other));
        // A diff of the working tree read again is other content.
        assertEquals(Scrolled.top(C), scrolled.scrolled("diff", C));
    }

    @Test
    void eachAreaHasItsOwnPosition() {
        assertEquals(Scrolled.top(AB), scrolled.scrolled("log", AB));
    }
}
