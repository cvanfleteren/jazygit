package net.vanfleteren.jazygit.kernel.state;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.Fixtures;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.RepoSlug;
import net.vanfleteren.jazygit.core.repo.Sha;
import org.junit.jupiter.api.Test;

/**
 * What the features state the branches are built on, as the kernel composes it from the parts they keep (ADR 169).
 */
class BranchBasesTest {

    private static final Option<BranchName> MAIN = Option.of(name("main"));

    private static final Part<StatedBases> FIRST =
            Part.of("a.bases", StatedBases.NONE).stating(bases -> bases).naming(bases -> Option.none());

    private static final Part<StatedBases> SECOND = Part.of("b.bases", StatedBases.NONE)
            .stating(bases -> bases)
            .naming(bases -> Option.of(new RepoSlug("owner/" + bases.roots().size())));

    private static BranchName name(String name) {
        return new BranchName(name);
    }

    private static Branch branch(String name) {
        return Fixtures.branch(name(name), false, new Sha("aaaa"));
    }

    private static StatedBases stating(String head, String base, String by, String... roots) {
        return new StatedBases(
                HashMap.of(name(head), new StatedBases.Stated(name(base), Phrase.verbatim(by))),
                HashSet.of(roots).map(BranchBasesTest::name),
                Option.none());
    }

    @Test
    void withoutAPartThatStatesThemNothingIsStated() {
        assertEquals(BranchBases.NONE, BranchBases.in(TestModels.loaded()));
        assertEquals(Option.none(), TestModels.loaded().parts().repository());
    }

    @Test
    void aBranchIsARootWhenAnyPartSaysSo() {
        BranchBases bases = BranchBases.in(TestModels.loaded()
                .withPart(FIRST, stating("feat/a", "main", "a", "develop"))
                .withPart(SECOND, stating("feat/b", "main", "b", "release")));

        assertEquals(true, bases.root(name("develop"), MAIN));
        assertEquals(true, bases.root(name("release"), MAIN));
        assertEquals(true, bases.root(name("main"), MAIN));
        assertEquals(false, bases.root(name("feat/a"), MAIN));
    }

    @Test
    void theBaseOfABranchIsTheOneTheFirstPartThatStatesOneStates() {
        BranchBases bases = BranchBases.in(TestModels.loaded()
                .withPart(SECOND, stating("feat/b", "develop", "b"))
                .withPart(FIRST, stating("feat/b", "feat/a", "a")));

        assertEquals(
                Option.of(new StatedBases.Stated(name("feat/a"), Phrase.verbatim("a"))), bases.of(branch("feat/b")));
        assertEquals(
                HashMap.of(name("feat/b"), name("feat/a")),
                bases.parents(
                        List.of(branch("main"), branch("develop"), branch("feat/a"), branch("feat/b")),
                        HashSet.empty(),
                        MAIN,
                        HashMap.empty()));
    }

    @Test
    void aPartThatStatesNothingIsLeftOut() {
        Part<Integer> silent = Part.of("c.count", 0).stating(count -> StatedBases.NONE);
        Model model = TestModels.loaded()
                .withPart(FIRST, stating("feat/a", "main", "a"))
                .withPart(silent, 3);

        assertEquals(
                List.of(stating("feat/a", "main", "a")), BranchBases.in(model).stated());
    }

    @Test
    void theRepositoryIsNamedByThePartThatKnowsIt() {
        Model model = TestModels.loaded()
                .withPart(FIRST, stating("feat/a", "main", "a"))
                .withPart(SECOND, stating("feat/b", "main", "b", "develop"));

        assertEquals(Option.of("owner/1"), model.parts().repository());
    }
}
