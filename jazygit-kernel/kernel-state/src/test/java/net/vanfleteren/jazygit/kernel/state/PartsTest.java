package net.vanfleteren.jazygit.kernel.state;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.git.model.Fixtures;
import net.vanfleteren.jazygit.core.repo.Commit;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.load.LoadStatus;
import net.vanfleteren.jazygit.kernel.state.load.Target;
import net.vanfleteren.jazygit.lib.lens.Lens;
import org.junit.jupiter.api.Test;

class PartsTest {

    private static final Part<Option<String>> SHOWN = Part.of("test.shown", Option.none());

    private static final Part<Integer> COUNT = Part.of("test.count", 0);

    private static final Commit FOUND =
            Fixtures.commit(new Sha("0123"), "Bob", "bob@example.com", Instant.EPOCH, "Fix it", "");

    /**
     * Which parts answered an event, in the order they did.
     */
    private static final Part<List<String>> ANSWERED = Part.of("test.answered", List.empty());

    private static Part<Integer> answering(String name) {
        return Part.of("test." + name, 0)
                .reacting((model, value, event) ->
                        Update.Next.of(model.mapPart(ANSWERED, answered -> answered.append(name))));
    }

    @Test
    void aPartNeverSetHasItsInitialValue() {
        Model model = Model.initial("repo");

        assertEquals(Option.none(), model.part(SHOWN));
        assertEquals(0, model.part(COUNT));
    }

    @Test
    void aPartKeepsWhatItIsSetTo() {
        Model model = Model.initial("repo").withPart(SHOWN, Option.of("x")).mapPart(COUNT, count -> count + 2);

        assertEquals(Option.of("x"), model.part(SHOWN));
        assertEquals(2, model.part(COUNT));
    }

    @Test
    void aPartSetBackToItsInitialValueLeavesTheModelEqualToOneWithoutIt() {
        Model initial = Model.initial("repo");

        Model back = initial.withPart(SHOWN, Option.of("x")).withPart(SHOWN, Option.none());

        assertEquals(initial, back);
        assertTrue(back.parts().kept().isEmpty());
    }

    @Test
    void aPartIsKeptUnderItsNameSoTwoPartsOfOneNameShareTheirValue() {
        Part<Option<String>> same = Part.of("test.shown", Option.none());

        Model model = Model.initial("repo").withPart(SHOWN, Option.of("x"));

        assertEquals(SHOWN, same);
        assertEquals(Option.of("x"), model.part(same));
    }

    @Test
    void theLensOfAPartReadsAndChangesIt() {
        Model model = COUNT.lens().map(Model.initial("repo"), count -> count + 5);

        assertEquals(Option.of(5), COUNT.lens().get(model));
    }

    @Test
    void onlyThePartsThatAreKeptAreAskedWhatToReadAgain() {
        Part<Integer> rereading = Part.of("test.rereading", 0).rereading((model, value) -> List.of(new LoadStatus()));

        assertEquals(List.empty(), Model.initial("repo").parts().rereads(Model.initial("repo")));
        Model kept = Model.initial("repo").withPart(rereading, 1);
        assertEquals(List.of(new LoadStatus()), kept.parts().rereads(kept));
    }

    @Test
    void theModelKnowsTheCommitsAPartKeptLists() {
        Part<Option<Commit>> listing = Part.of("test.listing", Option.<Commit>none())
                .knowingCommits(
                        (value, sha) -> value.filter(commit -> commit.sha().equals(sha)));

        Model model = Model.initial("repo");

        assertEquals(Option.none(), model.commit(FOUND.sha()));
        assertEquals(Option.of(FOUND), model.withPart(listing, Option.of(FOUND)).commit(FOUND.sha()));
        assertEquals(Option.none(), model.withPart(listing, Option.of(FOUND)).commit(new Sha("4567")));
    }

    @Test
    void thePartsThatAreKeptAnswerAnEventInTheOrderOfTheirNames() {
        Part<Integer> first = answering("a");
        Part<Integer> second = answering("b");
        Part<Integer> notKept = answering("c");
        Model model = Model.initial("repo").withPart(second, 1).withPart(first, 1);

        Model answered = Parts.reacting(model, new KernelEvent.LogRead()).model();

        assertEquals(List.of("a", "b"), answered.part(ANSWERED));
        assertEquals(0, answered.part(notKept));
    }

    @Test
    void aPartThatAnEarlierOneStoppedKeepingDoesNotAnswer() {
        Part<Integer> second = answering("b");
        Part<Integer> first =
                Part.of("test.a", 0).reacting((model, value, event) -> Update.Next.of(model.withPart(second, 0)));
        Model model = Model.initial("repo").withPart(first, 1).withPart(second, 1);

        Model answered = Parts.reacting(model, new KernelEvent.LogRead()).model();

        assertEquals(List.empty(), answered.part(ANSWERED));
    }

    @Test
    void aLoadKeepsWhatItReadInAPartOnlyWhileThePartIsKept() {
        Part<Loadable<String>> read = Part.of("test.read", new Loadable.Loaded<>(""));
        Target<String> target = Target.inPart(read, Lens.of(value -> value, (value, now) -> now));
        Model reading = Model.initial("repo").withPart(read, Loadable.loading());

        assertEquals(new Loadable.Loaded<>("x"), target.reload(reading, "x").part(read));
        assertEquals(Model.initial("repo"), target.reload(Model.initial("repo"), "x"));
    }
}
