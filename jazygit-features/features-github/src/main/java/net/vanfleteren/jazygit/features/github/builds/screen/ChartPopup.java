package net.vanfleteren.jazygit.features.github.builds.screen;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.state.popup.Popup;

/**
 * Asking how many of the newest runs the chart of the builds screen shows (ADR 212).
 *
 * @param window how many show now; none for as many as fit
 */
public record ChartPopup(Option<Integer> window) implements Popup {}
