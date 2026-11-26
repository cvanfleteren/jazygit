package net.vanfleteren.jazygit.kernel.state;

/**
 * The id of a feature that is on or off for a repository, by which the model keeps whether it is on (ADR 171): each
 * such feature declares its own once.
 */
public record FeatureId(String value) {}
