package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.CheckRun;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Check;

/**
 * That the checks of a branch that are not done all wait to start, as the branches pane shows it (ADR 35).
 *
 * @param since when the first of them started to wait: when its workflow run was created; none when they are all of
 *              re-runs, as GitHub does not say when an attempt started
 */
public record Waiting(Option<Instant> since) {

    /**
     * {@code queued}, checks that wait to start, waiting since the first workflow run of them was created.
     */
    public static Waiting of(List<Check> queued) {
        return new Waiting(queued.flatMap(Check::run)
                .filter(run -> run.runAttempt() == 1)
                .map(CheckRun::runCreatedAt)
                .min());
    }
}
