package net.vanfleteren.jazygit.features.chrome.settings;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.state.forge.ForgeCandidate;
import net.vanfleteren.jazygit.kernel.state.forge.ForgeChoicePopup;
import net.vanfleteren.jazygit.kernel.state.forge.ForgeMsg;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.widgets.ChoiceDialog;
import net.vanfleteren.jazygit.kernel.ui.widgets.ChoiceDialog.Choice;

/**
 * Asking which forge the repository is on (ADR 177): each remote a forge claimed, by its forge, its name and its URL,
 * numbered, and no forge; the one the rule picks highlighted. Escape puts the choice off until the repository is
 * opened again.
 */
public final class ForgeChoiceDialog {

    // The most remotes listed, each by its digit.
    private static final int LISTED = 9;

    private static final char NONE_KEY = 'n';

    public static final ChoiceDialog.Spec<ForgeChoicePopup> SPEC = new ChoiceDialog.Spec<>(
                    "forge-choice",
                    ForgeChoicePopup.class,
                    popup -> Messages.get("chrome.dialog.forge.title"),
                    List.range(0, LISTED)
                            .map(ForgeChoiceDialog::candidate)
                            .append(new Choice<ForgeChoicePopup>(
                                            NONE_KEY,
                                            "chrome.dialog.forge.none",
                                            () -> new ForgeMsg.Chosen(Option.none()))
                                    .withDetails("chrome.dialog.forge.none.details")),
                    80)
            .cancelledBy(ForgeMsg.Later::new)
            .openingAt(popup -> popup.pick()
                    .map(pick -> popup.candidates().indexOf(pick))
                    .filter(index -> index >= 0 && index < LISTED)
                    .map(ForgeChoiceDialog::key));

    private ForgeChoiceDialog() {}

    private static char key(int index) {
        return (char) ('1' + index);
    }

    /**
     * The choice of the {@code index}th remote claimed, shown only when there is one.
     */
    private static Choice<ForgeChoicePopup> candidate(int index) {
        return new Choice<ForgeChoicePopup>(
                        key(index),
                        popup -> nth(popup, index)
                                .map(candidate -> Messages.get(
                                        "chrome.dialog.forge.remote",
                                        candidate.forge(),
                                        candidate.remote().value(),
                                        candidate.url().value()))
                                .getOrElse(""),
                        () -> new ForgeMsg.Picked(index))
                .withDetails("chrome.dialog.forge.remote.details")
                .shownWhen((model, popup) -> nth(popup, index).isDefined());
    }

    private static Option<ForgeCandidate> nth(ForgeChoicePopup popup, int index) {
        return popup.candidates().drop(index).headOption();
    }
}
