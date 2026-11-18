package net.vanfleteren.jazygit.changes.diff;

import net.vanfleteren.jazygit.ui.Feature;
import net.vanfleteren.jazygit.ui.FeatureAction;

/**
 * The diffs: the files of a commit or stash, expanding and folding the context, the versions of a file shown, and
 * searching what the content panel shows (ADR 126), going to a line of it (ADR 140), the options of how the diffs are
 * read and drawn, and the filters that hide hunks of them (ADR 144).
 */
public final class DiffFeature {

    /**
     * What the app knows of it: its keys and its popups.
     */
    public static final Feature FEATURE = Feature.of(
            CommitFilesAction.ACTION,
            ExpandAction.ACTION,
            FoldAction.ACTION,
            VersionAction.ACTION,
            ContentSearchAction.ACTION,
            GoToLineAction.ACTION,
            FeatureAction.ofPopups(DiffOptionsDialog::popups),
            FeatureAction.ofPopups(DiffFiltersDialog::popups));

    private DiffFeature() {}
}
