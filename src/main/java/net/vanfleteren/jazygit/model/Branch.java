package net.vanfleteren.jazygit.model;

/**
 * A git branch, with a flag indicating whether it is the currently checked-out branch.
 *
 * @param tipOid the commit the branch points to
 */
public record Branch(String name, boolean current, String tipOid) {
}
