package net.vanfleteren.jazygit.kernel.state;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.state.forge.ForgeClaim;

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
     * What one key sends that several features bind alike, as two forges do (ADR 175): what the first of
     * {@code sent} whose feature is on sends, or nothing when none is.
     */
    record FirstOn(List<Owned> sent) implements FeatureMsg {

        /**
         * What the first feature that is on in {@code model} sends, if one is.
         */
        public Option<Owned> on(Model model) {
            return sent.find(owned -> model.on(owned.feature()));
        }
    }

    /**
     * The feature {@code feature} is switched on when {@code on}, else off: as the kernel decided the forge of the
     * repository (ADR 177).
     */
    record Applies(String feature, boolean on) implements FeatureMsg {}

    /**
     * A forge found which remotes of the repository are on it, as it opened (ADR 177): the kernel decides from what
     * every forge claimed which one is on.
     */
    record Claimed(ForgeClaim claim) implements FeatureMsg {}
}
