package net.vanfleteren.jazygit.kernel.state;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.kernel.state.Reactions.Reaction;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.index.RefreshIndex;
import net.vanfleteren.jazygit.kernel.state.load.LoadStatus;
import net.vanfleteren.jazygit.kernel.state.load.LoadWorktrees;
import org.junit.jupiter.api.Test;

/**
 * What the features answer of what happened in the kernel.
 */
class EventsTest {

    /**
     * What was answered, in the order it was.
     */
    private static final Part<List<String>> ANSWERED = Part.of("test.events.answered", List.empty());

    private static Reaction noting(String name) {
        return (model, event) -> Next.of(model.mapPart(
                ANSWERED,
                answered -> answered.append(name + " " + event.getClass().getSimpleName())));
    }

    private static Model withReactions(Reaction... reactions) {
        return Model.initial("repo").withReactions(new Reactions(List.of(reactions)));
    }

    @Test
    void aModelWithNoReactionsIsLeftAsItIs() {
        Model model = Model.initial("repo");

        assertEquals(Next.of(model), Events.happened(Next.of(model), new KernelEvent.Fetched()));
    }

    @Test
    void eachReactionAnswersInOrderWithTheModelTheEarlierOnesLeft() {
        Model model = withReactions(noting("a"), noting("b"));

        Next next = Events.happened(Next.of(model), new KernelEvent.OperationDone(true));

        assertEquals(List.of("a OperationDone", "b OperationDone"), next.model().part(ANSWERED));
    }

    @Test
    void whatEachAsksForIsAskedForAfterWhatCameBefore() {
        Cmd before = new RefreshIndex();
        Cmd first = new LoadStatus();
        Cmd second = new LoadWorktrees();
        Model model = withReactions((m, event) -> Next.of(m, first), (m, event) -> Next.of(m, second));

        Next next = Events.happened(Next.of(model, before), new KernelEvent.RepositoryOpened());

        assertEquals(List.of(before, first, second), next.cmds());
    }

    @Test
    void thePartsThatAreKeptAnswerBeforeTheReactionsDo() {
        Part<Integer> part = Part.of("test.part", 0)
                .reacting(
                        (model, value, event) -> Next.of(model.mapPart(ANSWERED, answered -> answered.append("part"))));
        Model model = withReactions(noting("reaction")).withPart(part, 1);

        Next next = Events.happened(Next.of(model), new KernelEvent.LogRead());

        assertEquals(List.of("part", "reaction LogRead"), next.model().part(ANSWERED));
    }
}
