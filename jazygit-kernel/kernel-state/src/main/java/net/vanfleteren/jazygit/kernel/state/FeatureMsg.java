package net.vanfleteren.jazygit.kernel.state;

/**
 * What a feature that is on or off for a repository sends (ADR 171).
 */
public sealed interface FeatureMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return FeatureUpdate.update(model, this);
    }

    /**
     * {@code msg}, of the feature {@code feature}, as a key or a timer of it sends it: it does something only while
     * the feature is on.
     */
    record Owned(String feature, Msg msg) implements FeatureMsg {}

    /**
     * The feature {@code feature} applies to the repository when {@code on}, as it found out.
     */
    record Applies(String feature, boolean on) implements FeatureMsg {}
}
