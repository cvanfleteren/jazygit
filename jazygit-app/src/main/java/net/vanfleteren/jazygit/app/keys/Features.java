package net.vanfleteren.jazygit.app.keys;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.control.Option;
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
import net.vanfleteren.jazygit.kernel.state.Reactions;
import net.vanfleteren.jazygit.kernel.ui.Feature;

/**
 * The features of the app, which the standard keymap, the popups, the timers and the reactions to the kernel are made of
 * (ADR 127). Adding a feature is
 * declaring its {@link Feature} in its package, and listing it here.
 */
public final class Features {

    /**
     * The system property naming, separated by commas, the ids of the features the app runs without, as if they were
     * deleted ({@code --without}): set before anything asks for {@link #ALL}, which is read once.
     */
    public static final String WITHOUT = "jazygit.without";

    /**
     * Every feature there is, in the order their keys are listed in a panel; those with only popups come last. The
     * order of the popups does not matter, as only one is open.
     */
    private static final List<Feature> DECLARED = List.of(
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
                    ErrorFeature.FEATURE)
            // As the app runs them: a feature that is off for a repository shows and runs nothing (ADR 171).
            .map(Feature::gated);

    /**
     * Every feature the app runs with: those {@link #DECLARED}, but those {@link #WITHOUT} names.
     */
    public static final List<Feature> ALL = without(
            DECLARED,
            Option.of(System.getProperty(WITHOUT)).map(Features::named).getOrElse(HashSet.empty()));

    /**
     * What every feature answers of what happens in the kernel, which the model holds (ADR 158), in the order of the
     * features.
     */
    public static final Reactions REACTIONS = new Reactions(ALL.flatMap(Feature::reactions));

    private Features() {}

    /**
     * The ids {@code --without} takes: those of the features that decide whether they apply to a repository.
     */
    public static Set<String> ids() {
        return ids(DECLARED);
    }

    static Set<String> ids(List<Feature> features) {
        return features.flatMap(feature -> feature.applying().map(Feature.Applying::id))
                .toLinkedSet();
    }

    /**
     * {@code features}, but those whose id is one of {@code ids}.
     */
    static List<Feature> without(List<Feature> features, Set<String> ids) {
        return features.reject(feature -> feature.applying().exists(applying -> ids.contains(applying.id())));
    }

    private static Set<String> named(String property) {
        return HashSet.of(property.split(",")).map(String::strip).reject(String::isEmpty);
    }
}
