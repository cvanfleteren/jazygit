package net.vanfleteren.jazygit.kernel.ui.keys;

/**
 * What a symbol of a panel means, as the help of the panel lists it under its keys (ADR 168): the panel says what its
 * own symbols mean, and a feature that draws in it adds what its symbols do.
 *
 * @param scopeId        the id of the scope of the panel
 * @param symbols        the symbols, as they are drawn, a space apart when there are more of a kind
 * @param descriptionKey the message key of what they mean
 */
public record Legend(String scopeId, String symbols, String descriptionKey) {}
