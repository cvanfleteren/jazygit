package net.vanfleteren.jazygit.kernel.state;

import io.vavr.collection.HashSet;
import io.vavr.collection.Set;
import io.vavr.control.Option;

/**
 * Which features are off for the repository shown (ADR 171): a feature decides whether it applies to a repository,
 * and while it is off, nothing of it shows or runs. A feature that decides nothing is always on.
 *
 * @param off the ids of the features that are off
 */
public record FeatureSwitches(Set<FeatureId> off) {

    /**
     * Every feature on.
     */
    public static final FeatureSwitches ALL_ON = new FeatureSwitches(HashSet.empty());

    /**
     * Where they are kept in the model.
     */
    // Another worktree of the same repository is on the same forge (ADR 182): they stay as they are, unless the forges
    // decide again as it opens.
    public static final Part<FeatureSwitches> PART =
            Part.of("kernel.features", ALL_ON).carrying(Option::of);

    /**
     * Whether the feature {@code feature} is on.
     */
    public boolean on(FeatureId feature) {
        return !off.contains(feature);
    }

    /**
     * These with the feature {@code feature} on when {@code on}, else off.
     */
    public FeatureSwitches switched(FeatureId feature, boolean on) {
        return new FeatureSwitches(on ? off.remove(feature) : off.add(feature));
    }
}
