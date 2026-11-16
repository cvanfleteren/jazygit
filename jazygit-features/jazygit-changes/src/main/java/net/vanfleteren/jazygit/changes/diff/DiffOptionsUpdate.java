package net.vanfleteren.jazygit.changes.diff;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.UnaryOperator;
import net.vanfleteren.jazygit.git.model.DiffContext;
import net.vanfleteren.jazygit.git.model.DiffOptions;
import net.vanfleteren.jazygit.settings.DiffStyle;
import net.vanfleteren.jazygit.settings.Settings;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.RereadUpdate;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.load.DiffLoad;
import net.vanfleteren.jazygit.state.load.LoadFileDiff;
import net.vanfleteren.jazygit.state.preferences.SaveSettings;
import net.vanfleteren.jazygit.state.view.BranchCommitsView;
import net.vanfleteren.jazygit.state.view.CommitFilesView;
import net.vanfleteren.jazygit.state.view.DivergenceView;
import net.vanfleteren.jazygit.state.view.Preview;

/**
 * How the diffs are read, and how the diffs of the working tree, of a commit and of a stash are drawn. Changing either
 * reads the diffs that are shown again, which keep showing as they were until they are read; what was read before the
 * change is dropped when it arrives. The style decides how a merge is read (ADR 44), so it changes how the diffs are
 * read too. The amount of context, like ignoring whitespace, holds for this session only (ADR 76).
 */
public final class DiffOptionsUpdate {

    private DiffOptionsUpdate() {}

    public static Next update(Model model, DiffOptionsMsg msg) {
        return switch (msg) {
            // The keys work everywhere, but not in a dialog, whose diff does not change.
            case DiffOptionsMsg m when model.popup().isDefined() -> Next.of(model);
            case DiffOptionsMsg.WhitespaceToggled() ->
                changed(model.mapPreferences(
                        preferences -> preferences.withIgnoreWhitespace(!preferences.ignoreWhitespace())));
            case DiffOptionsMsg.StyleToggled() -> styled(model, DiffStyle::toggled);
            case DiffOptionsMsg.SplitToggled() -> styled(model, DiffStyle::splitToggled);
            case DiffOptionsMsg.WordsToggled() -> wordsToggled(model);
            case DiffOptionsMsg.ContextWidened() -> stepped(model, DiffContext::wider);
            case DiffOptionsMsg.ContextNarrowed() -> stepped(model, DiffContext::narrower);
        };
    }

    /**
     * Shows the amount of context {@code step} takes the current one to, for this session (ADR 76). On the last step
     * it changes nothing, so nothing is read again.
     */
    private static Next stepped(Model model, UnaryOperator<DiffContext> step) {
        DiffContext current = model.preferences().context();
        DiffContext next = step.apply(current);
        return next.equals(current)
                ? Next.of(model)
                : changed(model.mapPreferences(preferences -> preferences.withContext(next)));
    }

    /**
     * Draws the changes in the style {@code next} takes the current one to from now on, keeping it for the next
     * session. Between the plain view and a rich one the diffs are read again, as the plain view reads a merge
     * otherwise (ADR 44); between the rich and the split view they are only drawn again (ADR 139).
     */
    private static Next styled(Model model, UnaryOperator<DiffStyle> next) {
        Settings previous = model.preferences().settings();
        Settings chosen = previous.withDiffStyle(next.apply(previous.diffStyle()));
        Model styled = model.mapPreferences(preferences -> preferences.withSettings(chosen));
        SaveSettings save = new SaveSettings(previous, chosen);
        return chosen.diffStyle().rich() == previous.diffStyle().rich()
                ? Next.of(styled, save)
                : changed(styled).and(save);
    }

    /**
     * Highlights the changed words of a line from now on, or stops to, keeping it for the next session. It only
     * changes how the diffs are drawn, so they are not read again.
     */
    private static Next wordsToggled(Model model) {
        Settings previous = model.preferences().settings();
        Settings chosen = previous.withDiffWords(!previous.diffWords());
        return Next.of(
                model.mapPreferences(preferences -> preferences.withSettings(chosen)),
                new SaveSettings(previous, chosen));
    }

    /**
     * Reads the diffs shown in {@code model} again, with the options it has now, as after they changed in the
     * settings file (ADR 111).
     */
    public static Next changed(Model model) {
        DiffOptions options = model.preferences().diffOptions();
        Option<DivergenceView> divergence =
                model.repoView().branches().divergence().map(view -> view.withDetail(reloading(view.detail())));
        Option<BranchCommitsView> branchCommits =
                model.repoView().branches().branchCommits().map(view -> view.withDetail(reloading(view.detail())));
        Option<CommitFilesView> commitFiles =
                model.repoView().files().commitFiles().map(view -> view.withDiff(reloading(view.diff())));
        List<Cmd> loads = List.<Cmd>empty()
                .appendAll(model.repoView().preview().fileDiff().map(diff -> new LoadFileDiff(diff.key(), options)))
                .appendAll(RereadUpdate.shown(model).filter(load -> load instanceof DiffLoad));
        return new Next(
                model.mapRepoView(repoView -> repoView.mapPreview(
                                preview -> preview.withFileDiff(reloading(preview.fileDiff()))
                                        .withCommitDetail(reloading(preview.commitDetail()))
                                        .withStashDetail(reloading(preview.stashDetail())))
                        .mapBranches(
                                branches -> branches.withDivergence(divergence).withBranchCommits(branchCommits))
                        .mapFiles(files -> files.withCommitFiles(commitFiles))
                        // What was expanded was read with the options before, and is folded to their context.
                        .withoutExpansions()),
                loads);
    }

    /**
     * {@code preview} loading again, showing what it shows until it is read.
     */
    private static <K, V> Option<Preview<K, V>> reloading(Option<Preview<K, V>> preview) {
        return preview.map(shown -> Preview.loading(shown.key(), Option.of(shown)));
    }
}
