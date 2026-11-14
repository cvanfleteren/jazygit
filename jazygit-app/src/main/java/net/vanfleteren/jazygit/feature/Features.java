package net.vanfleteren.jazygit.feature;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.feature.branch.BranchFeature;
import net.vanfleteren.jazygit.feature.cherrypick.CherryPickFeature;
import net.vanfleteren.jazygit.feature.commit.CommitFeature;
import net.vanfleteren.jazygit.feature.copy.CopyFeature;
import net.vanfleteren.jazygit.feature.diff.DiffFeature;
import net.vanfleteren.jazygit.feature.discard.DiscardFeature;
import net.vanfleteren.jazygit.feature.edit.EditFeature;
import net.vanfleteren.jazygit.feature.error.ErrorFeature;
import net.vanfleteren.jazygit.feature.fetch.FetchFeature;
import net.vanfleteren.jazygit.feature.files.FilesFeature;
import net.vanfleteren.jazygit.feature.forcedelete.ForceDeleteFeature;
import net.vanfleteren.jazygit.feature.github.GitHubFeature;
import net.vanfleteren.jazygit.feature.help.HelpFeature;
import net.vanfleteren.jazygit.feature.inprogress.InProgressFeature;
import net.vanfleteren.jazygit.feature.repos.RecentReposFeature;
import net.vanfleteren.jazygit.feature.settings.SettingsFeature;
import net.vanfleteren.jazygit.feature.stage.StageFeature;
import net.vanfleteren.jazygit.feature.stash.StashFeature;
import net.vanfleteren.jazygit.feature.theme.ThemeFeature;
import net.vanfleteren.jazygit.feature.worktree.WorktreeFeature;

/**
 * The features of the app, which the standard keymap and the popups are made of (ADR 127). Adding a feature is
 * declaring its {@link Feature} in its package, and listing it here.
 */
public final class Features {

    /**
     * Every feature, in the order their keys are listed in a panel; those with only popups come last. The order of the
     * popups does not matter, as only one is open.
     */
    public static final List<Feature> ALL = List.of(
            StageFeature.FEATURE,
            CommitFeature.FEATURE,
            CherryPickFeature.FEATURE,
            DiscardFeature.FEATURE,
            EditFeature.FEATURE,
            StashFeature.FEATURE,
            BranchFeature.FEATURE,
            ForceDeleteFeature.FEATURE,
            GitHubFeature.FEATURE,
            WorktreeFeature.FEATURE,
            FilesFeature.FEATURE,
            FetchFeature.FEATURE,
            DiffFeature.FEATURE,
            CopyFeature.FEATURE,
            ThemeFeature.FEATURE,
            RecentReposFeature.FEATURE,
            SettingsFeature.FEATURE,
            InProgressFeature.FEATURE,
            HelpFeature.FEATURE,
            ErrorFeature.FEATURE);

    private Features() {}
}
