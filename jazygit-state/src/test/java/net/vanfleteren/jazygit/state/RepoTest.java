package net.vanfleteren.jazygit.state;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.Stash;
import org.junit.jupiter.api.Test;

/**
 * A stash is found by its sha among the stashes loaded.
 */
class RepoTest {

    private static final Stash WORK = new Stash("stash@{0}", new Sha("5555"), "work", Option.none(), Instant.EPOCH);
    private static final Stash OTHER = new Stash("stash@{1}", new Sha("6666"), "other", Option.none(), Instant.EPOCH);

    @Test
    void findsALoadedStashByItsSha() {
        Repo repo = Repo.LOADING.withStashes(new Loadable.Loaded<>(List.of(WORK, OTHER)));

        assertEquals(Option.of(OTHER), repo.stash(new Sha("6666")));
    }

    @Test
    void findsNoStashThatIsNotListed() {
        Repo repo = Repo.LOADING.withStashes(new Loadable.Loaded<>(List.of(WORK)));

        assertEquals(Option.none(), repo.stash(new Sha("6666")));
    }

    @Test
    void findsNoStashWhileTheyLoad() {
        assertEquals(Option.none(), Repo.LOADING.stash(new Sha("5555")));
    }
}
