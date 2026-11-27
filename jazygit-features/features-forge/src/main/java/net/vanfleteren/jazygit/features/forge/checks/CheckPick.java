package net.vanfleteren.jazygit.features.forge.checks;

import io.vavr.control.Option;

/**
 * Which check is picked in the checks popup (ADR 160), so that it stays picked while they are read again: by its name,
 * and its job when it is one.
 *
 * @param name the name of the check
 * @param job  the id of its job; none for a check that is not of the forge's Actions, or has no id yet
 */
public record CheckPick(String name, Option<Long> job) {

    /**
     * The pick of {@code check}.
     */
    public static CheckPick of(ForgeCheck check) {
        return new CheckPick(check.name(), check.job().flatMap(ForgeCheck.Job::id));
    }

    /**
     * Whether {@code check} is the one picked: of the same job, or of the same name without one. A forge that gives a
     * run run again new jobs makes its checks others.
     */
    public boolean matches(ForgeCheck check) {
        return of(check).equals(this);
    }
}
