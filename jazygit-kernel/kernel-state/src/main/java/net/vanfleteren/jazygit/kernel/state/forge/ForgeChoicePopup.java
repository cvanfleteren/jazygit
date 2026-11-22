package net.vanfleteren.jazygit.kernel.state.forge;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.state.popup.Popup;

/**
 * Asking the user which forge the repository is on, by the remote its pull requests are opened against (ADR 177).
 *
 * @param candidates the remotes the forges claimed
 * @param pick       the one highlighted as the dialog opens, if any
 */
public record ForgeChoicePopup(List<ForgeCandidate> candidates, Option<ForgeCandidate> pick) implements Popup {}
