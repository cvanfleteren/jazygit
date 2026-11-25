package net.vanfleteren.jazygit.features.changes.stage;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.FileEntry;
import net.vanfleteren.jazygit.core.git.model.MarkedFile;
import net.vanfleteren.jazygit.kernel.state.popup.Popup;

/**
 * Asking what to do with files in conflict that were asked to be staged, but still have conflict markers in them
 * (ADR 198). Staging owns it; the dialog that draws it is of {@code features-midway}, which offers to resolve them
 * as well.
 *
 * @param files     the files asked to be staged
 * @param directory the directory asked to be staged, if one was
 * @param marked    those of them with markers left, with where
 */
public record MarkersLeftPopup(List<FileEntry> files, Option<String> directory, List<MarkedFile> marked)
        implements Popup {}
