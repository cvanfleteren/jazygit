package net.vanfleteren.jazygit.kernel.ui.keys;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.state.FeatureId;

/**
 * What a symbol of a panel means, as the help of the panel lists it under its keys (ADR 168): the panel says what its
 * own symbols mean, and a feature that draws in it adds what its symbols do.
 *
 * @param scopeId        the id of the scope of the panel
 * @param symbols        the symbols, as they are drawn, a space apart when there are more of a kind
 * @param descriptionKey the message key of what they mean
 * @param feature        the id of the feature that adds it, when it is on or off for a repository: the help leaves it
 *                       out while it is off (ADR 171)
 */
public record Legend(String scopeId, String symbols, String descriptionKey, Option<FeatureId> feature) {

    /**
     * What a symbol means, as the panel or a feature that is always on says it.
     */
    public Legend(String scopeId, String symbols, String descriptionKey) {
        this(scopeId, symbols, descriptionKey, Option.none());
    }

    /**
     * This legend, of the feature {@code feature}.
     */
    public Legend of(FeatureId feature) {
        return new Legend(scopeId, symbols, descriptionKey, Option.of(feature));
    }
}
