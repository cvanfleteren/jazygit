package net.vanfleteren.jazygit.features.forgejo.builds;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.forgejo.CommitStatus;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.core.repo.Sha;
import org.junit.jupiter.api.Test;

/**
 * What another worktree of the same repository opens with of the builds on Forgejo (ADR 182).
 */
class ForgejoBuildsTest {

    private static final RemoteName ORIGIN = new RemoteName("origin");
    private static final Tip READ = new Tip(ORIGIN, new Sha("aaaa"));
    private static final Tip READING = new Tip(ORIGIN, new Sha("bbbb"));

    @Test
    void anotherWorktreeKeepsHowTheBuildsWentButReadsAgainWhatWasBeingRead() {
        ForgejoBuilds before = ForgejoBuilds.NONE
                .read(List.of(READ), HashMap.of(READ.sha(), CommitStatus.State.PASSED), HashSet.empty())
                .reading(List.of(READING));

        Option<ForgejoBuilds> carried = ForgejoBuilds.PART.hooks().carries().carried(before);

        assertEquals(Option.of(CommitStatus.State.PASSED), carried.flatMap(builds -> builds.state(READ.sha())));
        // The answer of the read that ran goes to the worktree shown before: it is due again here.
        assertEquals(List.of(READING), carried.get().due(List.of(READ, READING)));
    }
}
