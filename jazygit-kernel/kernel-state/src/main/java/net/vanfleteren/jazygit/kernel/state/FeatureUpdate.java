package net.vanfleteren.jazygit.kernel.state;

import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.preferences.Screen;

/**
 * Features on and off for a repository (ADR 171): what a feature that is off sends does nothing, and the features are
 * told when one is switched.
 */
public final class FeatureUpdate {

    private FeatureUpdate() {}

    public static Next update(Model model, FeatureMsg msg) {
        return switch (msg) {
            case FeatureMsg.Owned(String feature, Msg owned) -> model.on(feature) ? owned.apply(model) : Next.of(model);
            case FeatureMsg.Applies(String feature, boolean on) -> applies(model, feature, on);
        };
    }

    /**
     * {@code model} once the feature {@code feature} said whether it applies to the repository: the features are told
     * when that switches it, and a screen of its that is shown goes back to the repository's once it is off.
     */
    private static Next applies(Model model, String feature, boolean on) {
        if (model.on(feature) == on) {
            return Next.of(model);
        }
        Model switched = model.mapPart(FeatureSwitches.PART, switches -> switches.switched(feature, on));
        Model shown =
                !on && model.screen().feature().contains(feature) ? switched.withScreen(Screen.REPOSITORY) : switched;
        return Events.happened(Next.of(shown), new KernelEvent.Switched(feature, on));
    }

    /**
     * {@code model} with the feature {@code feature} off, until it says it applies: as a repository opens.
     */
    public static Model off(Model model, String feature) {
        return model.mapPart(FeatureSwitches.PART, switches -> switches.switched(feature, false));
    }
}
