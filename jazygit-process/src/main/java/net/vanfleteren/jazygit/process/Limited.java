package net.vanfleteren.jazygit.process;

/**
 * What was kept of a program's output within a {@link Limit}.
 *
 * @param text all of it, or its first whole lines when it was cut off
 * @param cut  whether the rest was left out
 */
public record Limited(String text, boolean cut) {}
