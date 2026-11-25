package net.vanfleteren.jazygit.features.forgejo.checks;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.forgejo.CommitStatus;
import net.vanfleteren.jazygit.core.forgejo.ForgejoCheck;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.Failure;

/**
 * Checks on Forgejo as the tests of the checks popup have them (ADR 195).
 */
final class ChecksFixtures {

    static final Instant NOW = Instant.parse("2026-11-25T12:00:00Z");
    static final Sha TIP = new Sha("2".repeat(40));
    static final RemoteBranch FEATURE = new RemoteBranch("origin", new BranchName("feature"));
    static final ForgejoChecksPopup LOADING = ForgejoChecksPopup.loading(
            new BranchRef.Local(new BranchName("feature")), FEATURE, TIP, NOW, Option.none());

    private ChecksFixtures() {}

    /**
     * A job of run {@code run} of Forgejo Actions, at {@code job} in it: started {@code started} seconds before NOW,
     * and done after {@code took} seconds, or running without.
     */
    static ForgejoCheck job(
            String name, CommitStatus.State state, long run, int job, int started, Option<Integer> took) {
        return new ForgejoCheck(
                name,
                state,
                state == CommitStatus.State.RUNNING ? RUNNING_JOB : "Done",
                Option.of("https://code.example.net/cvf/nix/actions/runs/" + run + "/jobs/" + job),
                NOW.minusSeconds(started + 5),
                Option.of(NOW.minusSeconds(started)),
                took.map(seconds -> NOW.minusSeconds(started - seconds)));
    }

    // What Forgejo Actions says of a job running.
    static final String RUNNING_JOB = "Has started running";

    /**
     * A job of run {@code run} that waits to run, posted {@code posted} seconds before NOW.
     */
    static ForgejoCheck queued(String name, long run, int job, int posted) {
        return new ForgejoCheck(
                name,
                CommitStatus.State.RUNNING,
                "Waiting to run",
                Option.of("https://code.example.net/cvf/nix/actions/runs/" + run + "/jobs/" + job),
                NOW.minusSeconds(posted),
                Option.none(),
                Option.none());
    }

    /**
     * A status of another CI, posted {@code posted} seconds before NOW.
     */
    static ForgejoCheck status(String name, CommitStatus.State state, int posted) {
        return new ForgejoCheck(
                name,
                state,
                "",
                Option.of("https://ci.example.net/1"),
                NOW.minusSeconds(posted),
                Option.of(NOW.minusSeconds(posted)),
                state == CommitStatus.State.RUNNING ? Option.none() : Option.of(NOW.minusSeconds(posted)));
    }

    static Either<Failure, ChecksRead> readOf(List<ForgejoCheck> checks) {
        return Either.right(new ChecksRead("cvf/nix", TIP, checks));
    }

    static ForgejoChecksPopup read(List<ForgejoCheck> checks) {
        return LOADING.with(readOf(checks));
    }
}
