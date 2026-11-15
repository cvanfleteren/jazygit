package net.vanfleteren.jazygit.feature.github.builds;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.Build;
import net.vanfleteren.jazygit.state.popup.Popup;

/**
 * Asking which build's jobs show on one time line (ADR 133).
 *
 * @param builds the builds of the tab that show, the newest first
 * @param picked the run of the build picked now; none for the last build that is done
 */
public record BuildPickPopup(List<Build> builds, Option<Long> picked) implements Popup {}
