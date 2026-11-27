package net.vanfleteren.jazygit.features.forge.checks;

import io.vavr.control.Option;

/**
 * Checks as a forge would give them to the checks flow, for its tests.
 */
final class ForgeChecks {

    private ForgeChecks() {}

    /**
     * The job {@code name} of run {@code run} of {@code workflow}, started by a push, as {@code state}; its id is
     * {@code id}.
     */
    static ForgeCheck job(String name, long run, long id, String workflow, ForgeCheck.State state) {
        return new ForgeCheck(
                name, state, Option.of(new ForgeCheck.Job(run, Option.of(id), workflow, "push")), new Read(name));
    }

    /**
     * The job {@code name} of run {@code run} of {@code workflow}, as {@code state}, with an id of its name.
     */
    static ForgeCheck job(String name, long run, String workflow, ForgeCheck.State state) {
        return job(name, run, name.hashCode(), workflow, state);
    }

    /**
     * The status {@code name} of another CI, as {@code state}.
     */
    static ForgeCheck status(String name, ForgeCheck.State state) {
        return new ForgeCheck(name, state, Option.none(), new Read(name));
    }

    /**
     * A check as the forge of the tests read it.
     */
    record Read(String name) implements ForgeCheck.Source {}
}
