package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import java.time.Duration;
import java.time.Instant;

/**
 * A read from GitHub that failed the last {@code times} times it ran, the last time at {@code since}. The timers
 * start it again only once it waited longer the more it failed: a repository without a remote on GitHub, gh logged
 * out or offline, keeps failing, and would else run gh a few times a minute for nothing (ADR 58). What the user does
 * still reads it at once.
 */
public record FailingRead(int times, Instant since) {

    /**
     * How long a read that failed once, twice, three times, or more, waits before the timers start it again. Once is
     * no wait: the next timer tries again, as a single failure is often a hiccup.
     */
    static final List<Duration> WAITS =
            List.of(Duration.ZERO, Duration.ofMinutes(2), Duration.ofMinutes(5), Duration.ofMinutes(10));

    /**
     * Whether the timers may start {@code read} at the time of {@code model}.
     */
    public static boolean due(Model model, Cmd read) {
        return model.gitHub()
                .failing()
                .get(read)
                .forAll(failing -> !model.now()
                        .isBefore(failing.since().plus(WAITS.get(Math.min(failing.times(), WAITS.size()) - 1))));
    }

    /**
     * {@code model} knowing that {@code read} just succeeded, or {@code failed}.
     */
    public static Model ran(Model model, Cmd read, boolean failed) {
        return model.mapGitHub(gitHub -> gitHub.withFailing(
                failed
                        ? gitHub.failing()
                                .put(
                                        read,
                                        new FailingRead(
                                                gitHub.failing()
                                                                .get(read)
                                                                .map(FailingRead::times)
                                                                .getOrElse(0)
                                                        + 1,
                                                model.now()))
                        : gitHub.failing().remove(read)));
    }
}
