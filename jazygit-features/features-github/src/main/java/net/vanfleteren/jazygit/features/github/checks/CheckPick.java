package net.vanfleteren.jazygit.features.github.checks;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.gh.model.CheckRun;
import net.vanfleteren.jazygit.core.gh.model.PullRequestDetails.Check;

/**
 * Which check is picked in the checks popup (ADR 160), so that it stays picked while they are read again: by its name,
 * and its job when it is one.
 *
 * @param name the name of the check
 * @param job  the id of its job; none for a check that is not of GitHub Actions
 */
public record CheckPick(String name, Option<Long> job) {

    /**
     * The pick of {@code check}.
     */
    public static CheckPick of(Check check) {
        return new CheckPick(check.name(), check.run().map(CheckRun::id));
    }

    /**
     * Whether {@code check} is the one picked: of the same job, or of the same name without one. A run run again has
     * new jobs, so its checks are not.
     */
    public boolean matches(Check check) {
        return of(check).equals(this);
    }
}
