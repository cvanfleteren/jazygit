package net.vanfleteren.jazygit.gh.model;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import org.junit.jupiter.api.Test;

/**
 * The reactions to a comment, as the viewer gives one or takes it back (ADR 86).
 */
class ReactionsTest {

    private static final Reactions SOME = new Reactions(
            List.of(new Reactions.Group(Reaction.THUMBS_UP, 2, false), new Reactions.Group(Reaction.EYES, 1, true)));

    @Test
    void aReactionGivenIsCountedInItsPlace() {
        assertEquals(
                new Reactions(List.of(
                        new Reactions.Group(Reaction.THUMBS_UP, 3, true), new Reactions.Group(Reaction.EYES, 1, true))),
                SOME.withViewer(Reaction.THUMBS_UP, true));
        assertEquals(
                new Reactions(List.of(
                        new Reactions.Group(Reaction.THUMBS_UP, 2, false),
                        new Reactions.Group(Reaction.HEART, 1, true),
                        new Reactions.Group(Reaction.EYES, 1, true))),
                SOME.withViewer(Reaction.HEART, true));
    }

    @Test
    void aReactionNobodyGivesAnyLongerIsLeftOut() {
        assertEquals(
                new Reactions(List.of(new Reactions.Group(Reaction.THUMBS_UP, 2, false))),
                SOME.withViewer(Reaction.EYES, false));
    }

    @Test
    void givingOneTheViewerGaveOrTakingBackOneTheyDidNotChangesNothing() {
        assertEquals(SOME, SOME.withViewer(Reaction.EYES, true));
        assertEquals(SOME, SOME.withViewer(Reaction.THUMBS_UP, false));
        assertTrue(SOME.viewerGave(Reaction.EYES));
        assertFalse(SOME.viewerGave(Reaction.THUMBS_UP));
    }
}
