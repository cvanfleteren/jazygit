package net.vanfleteren.jazygit.features.midway.resolve;

import net.vanfleteren.jazygit.features.changes.panes.files.FilesPane;
import net.vanfleteren.jazygit.features.midway.resolve.content.ResolvedFiles;
import net.vanfleteren.jazygit.kernel.ui.Feature;
import net.vanfleteren.jazygit.kernel.ui.FeatureAction;

/**
 * Resolving the files in conflict of what the repository is in the middle of, a file at a time (ADR 198).
 */
public final class ResolveFeature {

    /**
     * What the app knows of it: its keys and its popups, and the files it marks resolved in the files pane.
     */
    public static final Feature FEATURE = Feature.of(new FeatureAction(ResolveKeys::register, ResolveDialogs::popups))
            .filling(FilesPane.MARK, ResolvedFiles::mark)
            .filling(FilesPane.TITLE, ResolvedFiles::progress);

    private ResolveFeature() {}
}
