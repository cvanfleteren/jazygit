package net.vanfleteren.jazygit.model;

import java.time.Instant;

/**
 * A git branch, with a flag indicating whether it is the currently checked-out branch.
 *
 * @param tipOid  the commit the branch points to
 * @param tipTime when that commit was made
 */
public record Branch(String name, boolean current, String tipOid, Instant tipTime) {

    public Branch(String name, boolean current, String tipOid) {
        this(name, current, tipOid, Instant.EPOCH);
    }
}
