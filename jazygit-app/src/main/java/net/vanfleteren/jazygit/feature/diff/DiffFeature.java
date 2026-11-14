package net.vanfleteren.jazygit.feature.diff;

import net.vanfleteren.jazygit.feature.Feature;

/**
 * The diffs: the files of a commit or stash, expanding and folding the context, the versions of a file shown, and
 * searching what the content panel shows (ADR 126).
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
            ContentSearchAction.ACTION);

    private DiffFeature() {}
}
