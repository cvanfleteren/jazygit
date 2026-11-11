package net.vanfleteren.jazygit.feature.worktree.open;

import io.vavr.Tuple2;
import io.vavr.collection.List;
import net.vanfleteren.jazygit.settings.EditorProgram;
import net.vanfleteren.jazygit.state.popup.Popup;

/**
 * Asking which of the editors the user set opens the worktree at {@code worktree} (ADR 110).
 *
 * @param worktree where the worktree is, as text
 * @param editors  each editor with the letter that chooses it, in the order of the settings file
 */
public record OpenInEditorPopup(String worktree, List<Tuple2<Character, EditorProgram>> editors) implements Popup {}
