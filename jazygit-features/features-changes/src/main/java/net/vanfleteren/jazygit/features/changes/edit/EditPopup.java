package net.vanfleteren.jazygit.features.changes.edit;

import io.vavr.Tuple2;
import io.vavr.collection.List;
import net.vanfleteren.jazygit.kernel.settings.EditorProgram;
import net.vanfleteren.jazygit.kernel.state.popup.Popup;

/**
 * Asking which of the editors the user set to open files opens {@code path} (ADR 145).
 *
 * @param path    the file, relative to the working tree
 * @param line    the line it opens at, counted from 1
 * @param editors each editor that opens files with the letter that chooses it, in the order of the settings file
 */
public record EditPopup(String path, int line, List<Tuple2<Character, EditorProgram>> editors) implements Popup {}
