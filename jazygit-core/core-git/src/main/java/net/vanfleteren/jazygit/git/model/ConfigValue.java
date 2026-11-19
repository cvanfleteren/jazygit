package net.vanfleteren.jazygit.git.model;

/**
 * A value of a key of the git configuration, where git read it.
 *
 * @param local  whether it is in the repository's own configuration, or a file that one includes, rather than the
 *               user's or the system's
 * @param origin the file it is in, as git names it: relative to the repository for the repository's own
 *               configuration, else absolute
 * @param value  the value
 */
public record ConfigValue(boolean local, String origin, String value) {}
