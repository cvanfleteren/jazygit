package net.vanfleteren.jazygit.features.github.builds.screen;

import io.vavr.control.Either;
import net.vanfleteren.jazygit.kernel.state.Phrase;
import net.vanfleteren.jazygit.kernel.state.popup.Popup;

/**
 * Asking which runs of the workflow highlighted show on the builds screen (ADR 212).
 *
 * @param shown       which show now
 * @param defaultName the name of the default branch, once known
 * @param mine        the branch of the filter "mine"; else why there is none
 */
public record FilterPopup(BuildsScreen.Filter shown, String defaultName, Either<Phrase, String> mine)
        implements Popup {}
