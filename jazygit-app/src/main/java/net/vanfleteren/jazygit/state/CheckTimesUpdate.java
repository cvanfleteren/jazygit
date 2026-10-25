package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.UnaryOperator;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Check;
import net.vanfleteren.jazygit.gh.timing.CheckHistory;
import net.vanfleteren.jazygit.gh.timing.CheckSample;
import net.vanfleteren.jazygit.state.CheckTimesCmd.RecordCheckTimes;
import net.vanfleteren.jazygit.state.CheckTimesCmd.TopUpCheckTimes;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Learning how long the checks of the repository take, from every read that brings them with their timing, and
 * from the checks that ran lately on GitHub when what was learned is old (ADR 30). Nothing here bothers the user:
 * what cannot be kept or read only leaves estimates unknown.
 */
public final class CheckTimesUpdate {

    private CheckTimesUpdate() {}

    public static Next update(Model model, CheckTimesMsg msg) {
        return switch (msg) {
            case CheckTimesMsg.Recorded(String repository, CheckHistory history, boolean topUp) ->
                topUpIfDue(learned(model, repository, history), repository, topUp);
            case CheckTimesMsg.NotRecorded(String repository, List<CheckSample> samples, boolean topUp) ->
                topUpIfDue(
                        learned(
                                model,
                                repository,
                                known(model, repository)
                                        .map(CheckTimes::history)
                                        .getOrElse(CheckHistory.EMPTY)
                                        .with(samples, model.now())),
                        repository,
                        topUp);
            case CheckTimesMsg.ToppedUp(String repository, var recent) -> {
                Model done = mapKnown(model, repository, times -> times.withTopUpDone(model.now()));
                List<CheckSample> samples = recent.checks().flatMap(CheckSample::of);
                List<CheckSample> unknown = known(done, repository)
                        .map(times -> times.history().unknown(samples))
                        .getOrElse(samples);
                yield unknown.isEmpty()
                        ? Next.of(done)
                        : Next.of(done, new RecordCheckTimes(repository, unknown, model.now(), false));
            }
            case CheckTimesMsg.TopUpFailed(String repository) ->
                Next.of(mapKnown(model, repository, times -> times.withTopUpDone(model.now())));
        };
    }

    /**
     * {@code next}, learning from {@code checks}, which were just read of {@code repository}: they are kept when
     * they are new, and the history is read first when it was not yet. When one of them is not done, the checks that
     * ran lately are read from GitHub too, if what was learned is old.
     */
    public static Next read(Next next, String repository, List<Check> checks) {
        if (repository.isEmpty()) {
            return next;
        }
        Model model = next.model();
        boolean topUp = checks.exists(check -> !check.done());
        List<CheckSample> samples = checks.flatMap(CheckSample::of);
        Option<List<CheckSample>> unknown =
                known(model, repository).map(times -> times.history().unknown(samples));
        return unknown.exists(List::isEmpty)
                ? topUpIfDue(next, repository, topUp)
                : new Next(
                        model,
                        next.cmds()
                                .append(new RecordCheckTimes(
                                        repository, unknown.getOrElse(samples), model.now(), topUp)));
    }

    private static Next topUpIfDue(Model model, String repository, boolean topUp) {
        return topUpIfDue(Next.of(model), repository, topUp);
    }

    private static Next topUpIfDue(Next next, String repository, boolean topUp) {
        Model model = next.model();
        return topUp && known(model, repository).exists(times -> times.topUpDue(model.now()))
                ? new Next(
                        mapKnown(model, repository, CheckTimes::withTopUp),
                        next.cmds().append(new TopUpCheckTimes(repository)))
                : next;
    }

    private static Model learned(Model model, String repository, CheckHistory history) {
        return model.mapGitHub(gitHub -> gitHub.withCheckTimes(Option.of(known(model, repository)
                .map(times -> times.withHistory(history))
                .getOrElse(() -> CheckTimes.of(repository, history)))));
    }

    private static Option<CheckTimes> known(Model model, String repository) {
        return model.gitHub().checkTimes().filter(times -> times.repository().equals(repository));
    }

    private static Model mapKnown(Model model, String repository, UnaryOperator<CheckTimes> change) {
        return model.mapGitHub(gitHub -> gitHub.withCheckTimes(
                gitHub.checkTimes().map(times -> times.repository().equals(repository) ? change.apply(times) : times)));
    }
}
