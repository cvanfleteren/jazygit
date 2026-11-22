package net.vanfleteren.jazygit.kernel.state.forge;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.state.FeatureMsg;
import net.vanfleteren.jazygit.kernel.state.FeatureUpdate;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.operation.OperationUpdate;

/**
 * Which forge a repository is on (ADR 177): once every forge told its claims, and what the user chose before was
 * read, the kernel switches on the forge of the home, or asks the user which it is. It names no forge.
 */
public final class ForgeUpdate {

    private ForgeUpdate() {}

    public static Next update(Model model, ForgeMsg msg) {
        return switch (msg) {
            case ForgeMsg.Remembered(var chosen) ->
                decided(model.mapPart(ForgeHome.PART, home -> home.remembering(chosen)));
            case ForgeMsg.Chosen(var home) -> chosen(model.withoutPopup(ForgeChoicePopup.class), home);
            case ForgeMsg.Picked(int index) ->
                model.popup(ForgeChoicePopup.class)
                        .flatMap(popup -> popup.candidates().drop(index).headOption())
                        .map(home -> chosen(model.withoutPopup(ForgeChoicePopup.class), Option.of(home)))
                        .getOrElse(() -> Next.of(model));
            case ForgeMsg.Later() -> Next.of(model.withoutPopup(ForgeChoicePopup.class));
            case ForgeMsg.Asked() -> asked(model);
        };
    }

    /**
     * {@code model} waiting for the claims of {@code feature}, as the repository opens, and reading what the user
     * chose before, once for every forge.
     */
    public static Next opening(Model model, String feature) {
        return Next.of(model.mapPart(ForgeHome.PART, home -> home.awaiting(feature)), new ReadForgeChoice());
    }

    /**
     * {@code model} once a forge told {@code claim}.
     */
    public static Next claimed(Model model, ForgeClaim claim) {
        return decided(model.mapPart(ForgeHome.PART, home -> home.claimed(claim)));
    }

    /**
     * Decides, once everything it decides from is known: the forge of the home on, or the user asked.
     */
    private static Next decided(Model model) {
        ForgeHome home = model.part(ForgeHome.PART);
        if (!home.due()) {
            return Next.of(model);
        }
        return switch (ForgeDecision.of(home.claims(), home.remembered())) {
            case ForgeDecision.Home(ForgeCandidate chosen) -> switched(model, Option.of(chosen));
            case ForgeDecision.NoForge() -> Next.of(model.mapPart(ForgeHome.PART, h -> h.deciding(Option.none())));
            case ForgeDecision.Ask(var candidates, var pick) ->
                Next.of(model.mapPart(ForgeHome.PART, h -> h.deciding(Option.none()))
                        .openPopup(new ForgeChoicePopup(candidates, pick)));
        };
    }

    /**
     * {@code model} with {@code chosen} the home, kept so that it is not asked again.
     */
    private static Next chosen(Model model, Option<ForgeCandidate> chosen) {
        return switched(model, chosen)
                .then(switched ->
                        OperationUpdate.start(switched, new SaveForgeChoice(chosen.map(ForgeCandidate::remote))));
    }

    /**
     * {@code model} with the forge of {@code chosen} on, that of the home before off if it is another, and
     * {@code chosen} the home.
     */
    private static Next switched(Model model, Option<ForgeCandidate> chosen) {
        Option<String> before = model.part(ForgeHome.PART).home().map(ForgeCandidate::feature);
        Model decided = model.mapPart(ForgeHome.PART, home -> home.deciding(chosen));
        List<FeatureMsg.Applies> switches = before.filter(
                        feature -> !chosen.exists(c -> c.feature().equals(feature)))
                .map(feature -> new FeatureMsg.Applies(feature, false))
                .toList()
                .appendAll(chosen.map(c -> new FeatureMsg.Applies(c.feature(), true)));
        return switches.foldLeft(Next.of(decided), (next, applies) -> next.then(m -> FeatureUpdate.update(m, applies)));
    }

    /**
     * Asks again which forge the repository is on, from what the forges claimed as it opened, with the home
     * highlighted; nothing when no forge claimed a remote.
     */
    private static Next asked(Model model) {
        ForgeHome home = model.part(ForgeHome.PART);
        return home.candidates().isEmpty()
                ? Next.of(model)
                : Next.of(model.openPopup(new ForgeChoicePopup(home.candidates(), home.home())));
    }
}
