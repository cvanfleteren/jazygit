package net.vanfleteren.jazygit.state;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.vanfleteren.jazygit.git.model.DiffText;
import org.junit.jupiter.api.Test;

/**
 * Where the areas of the content panel are scrolled to: kept while an area shows the same content, from the top
 * for anything else.
 */
class ScrolledTest {

    private static final ScrolledContent AB = new ScrolledContent.Diff(DiffText.of("+a\n+b\n"));
    private static final ScrolledContent C = new ScrolledContent.Diff(DiffText.of("+c\n"));

    private final Model scrolled = Update.update(
                    TestModels.loaded(), new Msg.ContentScrolled("diff", new Scrolled(AB, 3, 1)))
            .model();

    @Test
    void anAreaShowsItsContentFromTheTopUntilItIsScrolled() {
        assertEquals(Scrolled.top(C), TestModels.loaded().scrolled("diff", C));
    }

    @Test
    void anAreaStaysWhereItWasScrolledToWhileItShowsTheSameContent() {
        assertEquals(new Scrolled(AB, 3, 1), scrolled.scrolled("diff", AB));
    }

    @Test
    void otherContentStartsAtTheTop() {
        assertEquals(Scrolled.top(C), scrolled.scrolled("diff", C));
    }

    @Test
    void eachAreaHasItsOwnPosition() {
        assertEquals(Scrolled.top(AB), scrolled.scrolled("log", AB));
    }
}
