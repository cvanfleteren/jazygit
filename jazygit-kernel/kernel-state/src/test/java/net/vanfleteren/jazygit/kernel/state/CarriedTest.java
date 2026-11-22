package net.vanfleteren.jazygit.kernel.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.control.Option;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * What a repository opened in place of the one shown carries of it (ADR 182): only another worktree of the same
 * repository carries anything, and it fetches at once unless the repository was fetched from.
 */
class CarriedTest {

    private static final Path COMMON = Path.of("/repo/.git");
    private static final Carried.Shown MAIN = new Carried.Shown(Path.of("/repo"), Option.of(COMMON));
    private static final Carried.Shown LINKED = new Carried.Shown(Path.of("/repo-wt"), Option.of(COMMON));
    private static final Part<Integer> SHARED = Part.of("test.shared", 0).carrying(Option::of);
    private static final Freshness FETCHED = Freshness.NEVER.succeeded("origin", Instant.EPOCH);

    private static Model last() {
        return Model.initial("repo").withPart(SHARED, 3).mapJobs(jobs -> jobs.withLastFetch(FETCHED));
    }

    @Test
    void anotherWorktreeOfTheSameRepositoryCarriesWhatTheWorktreesShare() {
        Carried carried = Carried.between(MAIN, LINKED, last());

        assertEquals(Carried.from(last()), carried);
        assertEquals(3, carried.into(Model.initial("repo")).part(SHARED));
    }

    @Test
    void theWorktreeShownOpenedAnewCarriesNothing() {
        // As a setting that only applies to a repository shown anew opens it again, to be read from nothing.
        assertEquals(Carried.NONE, Carried.between(MAIN, MAIN, last()));
    }

    @Test
    void anotherRepositoryCarriesNothing() {
        Carried.Shown other = new Carried.Shown(Path.of("/other"), Option.of(Path.of("/other/.git")));

        assertEquals(Carried.NONE, Carried.between(MAIN, other, last()));
    }

    @Test
    void aRepositoryWhoseCommonGitDirectoryIsNotKnownOnEitherSideCarriesNothing() {
        Carried.Shown unknown = new Carried.Shown(Path.of("/repo-wt"), Option.none());
        Carried.Shown unknownShown = new Carried.Shown(Path.of("/repo"), Option.none());

        assertEquals(Carried.NONE, Carried.between(MAIN, unknown, last()));
        assertEquals(Carried.NONE, Carried.between(unknownShown, unknown, last()));
    }

    @Test
    void aWorktreeOpenedWithTheLastFetchWaitsForTheTick() {
        assertTrue(Carried.between(MAIN, LINKED, last()).fetched());
    }

    @Test
    void aWorktreeOfARepositoryNeverFetchedFromFetchesAtOnceThoughItCarriesItsParts() {
        // The forges switched on and off as the repository opened carry, but no fetch was done yet.
        Model unfetched = FeatureUpdate.off(last().mapJobs(jobs -> jobs.withLastFetch(Freshness.NEVER)), "forge");

        Carried carried = Carried.between(MAIN, LINKED, unfetched);

        assertFalse(carried.into(Model.initial("repo")).on("forge"));
        assertFalse(carried.fetched());
        assertFalse(Carried.NONE.fetched());
    }
}
