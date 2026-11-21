package net.vanfleteren.jazygit.kernel.state;

import io.vavr.collection.HashSet;
import io.vavr.collection.Set;

/**
 * Which features are off for the repository shown (ADR 171): a feature decides whether it applies to a repository,
 * and while it is off, nothing of it shows or runs. A feature that decides nothing is always on.
 *
 * @param off the ids of the features that are off
 */
public record FeatureSwitches(Set<String> off) {

    /**
     * Every feature on.
     */
    public static final FeatureSwitches ALL_ON = new FeatureSwitches(HashSet.empty());

    /**
     * Where they are kept in the model.
     */
    public static final Part<FeatureSwitches> PART = Part.of("kernel.features", ALL_ON);

    /**
     * Whether the feature {@code feature} is on.
     */
    public boolean on(String feature) {
        return !off.contains(feature);
    }

    /**
     * These with the feature {@code feature} on when {@code on}, else off.
     */
    public FeatureSwitches switched(String feature, boolean on) {
        return new FeatureSwitches(on ? off.remove(feature) : off.add(feature));
    }
}
