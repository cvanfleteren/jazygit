package net.vanfleteren.jazygit.features.changes.panes.files;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.FileEntry;
import net.vanfleteren.jazygit.kernel.ui.widgets.LoadableList;

/**
 * What a feature shows of the files of the working tree in the files pane, in the model it gave it for: a feature
 * fills {@link FilesPane#MARK} with one (ADR 172).
 */
@FunctionalInterface
public interface FileMark {

    /**
     * What the feature shows of {@code file}, if anything.
     */
    Option<Mark> of(FileEntry file);

    /**
     * What a row shows of a file, after its name.
     *
     * @param after the segments after the name, each with a leading space
     * @param dims  whether the whole row is dimmed, as for a file listed only to be found again
     */
    record Mark(List<LoadableList.Seg> after, boolean dims) {}
}
