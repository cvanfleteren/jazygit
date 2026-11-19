package net.vanfleteren.jazygit.features.github.builds;

/**
 * What the keys of the builds of a branch act on, which have no cursor (ADR 131).
 *
 * @param searching whether a search of the content panel is kept: {@code n} and {@code N} then go to its matches,
 *                  which the area does itself, rather than to another build (ADR 126, 133)
 */
public record BuildsAt(boolean searching) {}
