package net.vanfleteren.jazygit.state;

import net.vanfleteren.jazygit.model.Branch;
import net.vanfleteren.jazygit.model.ChangeType;
import net.vanfleteren.jazygit.model.Commit;
import net.vanfleteren.jazygit.model.FileEntry;
import net.vanfleteren.jazygit.model.GitInfoProvider;
import net.vanfleteren.jazygit.model.RepoStatus;
import net.vanfleteren.jazygit.state.Loadable.Failed;
import net.vanfleteren.jazygit.state.Loadable.Loaded;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The runtime loop: commands are performed against the provider and their results fed back.
 * Both executors are queues drained by the test, so every step is deterministic.
 */
class ProgramTest {

    private static final class FakeProvider implements GitInfoProvider {

        RepoStatus status = new RepoStatus("main", "aaaa", List.of());
        RuntimeException statusError;
        int commitLoads;

        @Override
        public String repositoryName() {
            return "repo";
        }

        @Override
        public RepoStatus status() {
            if (statusError != null) {
                throw statusError;
            }
            return status;
        }

        @Override
        public List<Branch> branches() {
            return List.of(new Branch(status.head(), true));
        }

        @Override
        public List<Commit> commits() {
            commitLoads++;
            return List.of(new Commit(status.headOid(), "Ada", "2026-10-09", "msg", "msg"));
        }
    }

    private final FakeProvider provider = new FakeProvider();
    private final Queue<Runnable> io = new ArrayDeque<>();
    private final Queue<Runnable> ui = new ArrayDeque<>();
    private final Program program = Program.start(provider, io::add, ui::add);

    @Test
    void initialLoadFillsTheModel() {
        settle();

        Model model = program.model();
        assertEquals(new Loaded<>(provider.status), model.status());
        assertEquals(new Loaded<>(List.of(new Branch("main", true))), model.branches());
        assertEquals(1, provider.commitLoads);
    }

    @Test
    void ticksPickUpWorkingTreeChangesWithoutReloadingCommits() {
        settle();

        provider.status = new RepoStatus("main", "aaaa", List.of(new FileEntry("a.txt", ChangeType.UNTRACKED)));
        program.dispatch(new Msg.Tick());
        settle();

        assertEquals(new Loaded<>(provider.status), program.model().status());
        assertEquals(1, provider.commitLoads);
    }

    @Test
    void ticksReloadCommitsWhenHeadMoves() {
        settle();

        provider.status = new RepoStatus("main", "bbbb", List.of());
        program.dispatch(new Msg.Tick());
        settle();

        assertEquals(2, provider.commitLoads);
        assertEquals("bbbb", ((Loaded<List<Commit>>) program.model().commits()).value().get(0).shortSha());
    }

    @Test
    void providerErrorsBecomeFailedState() {
        provider.statusError = new IllegalStateException("git status failed");
        settle();

        assertEquals(new Failed<>("git status failed"), program.model().status());

        provider.statusError = null;
        program.dispatch(new Msg.Tick());
        settle();
        assertEquals(new Loaded<>(provider.status), program.model().status());
    }

    /**
     * Runs queued IO and UI work until both queues are empty.
     */
    private void settle() {
        while (!io.isEmpty() || !ui.isEmpty()) {
            drain(io);
            drain(ui);
        }
    }

    private static void drain(Queue<Runnable> queue) {
        Runnable task;
        while ((task = queue.poll()) != null) {
            task.run();
        }
    }
}
