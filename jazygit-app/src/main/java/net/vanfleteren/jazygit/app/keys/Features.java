package net.vanfleteren.jazygit.app.keys;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.features.agent.AgentFeature;
import net.vanfleteren.jazygit.features.branches.branch.BranchFeature;
import net.vanfleteren.jazygit.features.branches.forcedelete.ForceDeleteFeature;
import net.vanfleteren.jazygit.features.branches.repos.RecentReposFeature;
import net.vanfleteren.jazygit.features.branches.worktree.WorktreeFeature;
import net.vanfleteren.jazygit.features.changes.cherrypick.CherryPickFeature;
import net.vanfleteren.jazygit.features.changes.commit.CommitFeature;
import net.vanfleteren.jazygit.features.changes.copy.CopyFeature;
import net.vanfleteren.jazygit.features.changes.diff.DiffFeature;
import net.vanfleteren.jazygit.features.changes.discard.DiscardFeature;
import net.vanfleteren.jazygit.features.changes.edit.EditFeature;
import net.vanfleteren.jazygit.features.changes.fetch.FetchFeature;
import net.vanfleteren.jazygit.features.changes.files.FilesFeature;
import net.vanfleteren.jazygit.features.changes.inprogress.InProgressFeature;
import net.vanfleteren.jazygit.features.changes.stage.StageFeature;
import net.vanfleteren.jazygit.features.changes.stash.StashFeature;
import net.vanfleteren.jazygit.features.chrome.error.ErrorFeature;
import net.vanfleteren.jazygit.features.chrome.help.HelpFeature;
import net.vanfleteren.jazygit.features.chrome.settings.SettingsFeature;
import net.vanfleteren.jazygit.features.chrome.theme.ThemeFeature;
import net.vanfleteren.jazygit.features.github.GitHubFeature;
import net.vanfleteren.jazygit.kernel.ui.Feature;

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
            AgentFeature.FEATURE,
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
