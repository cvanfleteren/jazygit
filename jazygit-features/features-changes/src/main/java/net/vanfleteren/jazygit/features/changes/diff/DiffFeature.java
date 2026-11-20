package net.vanfleteren.jazygit.features.changes.diff;

import net.vanfleteren.jazygit.kernel.ui.Feature;
import net.vanfleteren.jazygit.kernel.ui.FeatureAction;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;

/**
 * The diffs: the files of a commit or stash, expanding and folding the context, the versions of a file shown, and
 * searching what the content panel shows (ADR 126), going to a line of it (ADR 140), the options of how the diffs are
 * read and drawn, and the filters that hide hunks of them (ADR 144).
 */
public final class DiffFeature {

    /**
     * The shortcuts of the diff options, which work wherever the focus is: the options themselves, the split, and the
     * context.
     */
    private static final FeatureAction GLOBAL_KEYS = FeatureAction.ofKeys(keys -> keys.global(
                    Keymap.Global.Dispatching.ofCtrl('d', "app.help.global.diffOptions", DiffOptionsMsg.Requested::new))
            .global(Keymap.Global.Dispatching.ofChar(
                    '|', "app.help.global.diffSplit", DiffOptionsMsg.SplitToggled::new))
            .global(Keymap.Global.Dispatching.ofChar(
                    '}', "app.help.global.moreContext", DiffOptionsMsg.ContextWidened::new))
            .global(Keymap.Global.Dispatching.ofChar(
                    '{', "app.help.global.lessContext", DiffOptionsMsg.ContextNarrowed::new)));

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
            GLOBAL_KEYS,
            FeatureAction.ofPopups(DiffOptionsDialog::popups),
            FeatureAction.ofPopups(DiffFiltersDialog::popups));

    private DiffFeature() {}
}
