package net.vanfleteren.jazygit.features.changes.diff;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.UnaryOperator;
import net.vanfleteren.jazygit.core.git.model.DiffContext;
import net.vanfleteren.jazygit.core.git.model.DiffOptions;
import net.vanfleteren.jazygit.kernel.settings.DiffStyle;
import net.vanfleteren.jazygit.kernel.settings.Settings;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.RereadUpdate;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.load.DiffLoad;
import net.vanfleteren.jazygit.kernel.state.load.LoadFileDiff;
import net.vanfleteren.jazygit.kernel.state.load.ReadBlame;
import net.vanfleteren.jazygit.kernel.state.preferences.SaveSettings;
import net.vanfleteren.jazygit.kernel.state.view.BranchCommitsView;
import net.vanfleteren.jazygit.kernel.state.view.CommitFilesView;
import net.vanfleteren.jazygit.kernel.state.view.DivergenceView;
import net.vanfleteren.jazygit.kernel.state.view.Preview;

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
            // Not while a dialog is open, whose diff does not change, but for the popup of the options, which
            // sends these itself: the keys of their own do not reach past an open popup.
            case DiffOptionsMsg m
            when model.popup().isDefined()
                    && model.popup(DiffOptionsPopup.class).isEmpty() -> Next.of(model);
            case DiffOptionsMsg.Requested() -> Next.of(model.openPopup(DiffOptionsPopup.of(model)));
            // A version blamed is blamed again, as whitespace changes whom a line is blamed on (ADR 147).
            case DiffOptionsMsg.WhitespaceToggled() ->
                shown(changed(model.mapPreferences(
                                preferences -> preferences.withIgnoreWhitespace(!preferences.ignoreWhitespace())))
                        .then(ReadBlame::blamed));
            case DiffOptionsMsg.StyleChosen(DiffStyle style) -> shown(styled(model, previous -> style));
            case DiffOptionsMsg.SplitToggled() -> shown(styled(model, DiffStyle::splitToggled));
            case DiffOptionsMsg.WordsToggled() -> shown(wordsToggled(model));
            case DiffOptionsMsg.PairsToggled() -> shown(pairsToggled(model));
            case DiffOptionsMsg.ContextWidened() -> shown(stepped(model, DiffContext::wider));
            case DiffOptionsMsg.ContextNarrowed() -> shown(stepped(model, DiffContext::narrower));
        };
    }

    /**
     * {@code model} with the popup of the options showing them as they are now, if it is open.
     */
    static Model shown(Model model) {
        return model.popup(DiffOptionsPopup.class)
                .map(open -> model.openPopup(DiffOptionsPopup.of(model)))
                .getOrElse(model);
    }

    private static Next shown(Next next) {
        return new Next(shown(next.model()), next.cmds());
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
        if (chosen.equals(previous)) {
            return Next.of(model);
        }
        Model styled = model.mapPreferences(preferences -> preferences.withSettings(chosen));
        SaveSettings save = new SaveSettings(previous, chosen);
        return chosen.diffStyle().rich() == previous.diffStyle().rich()
                ? Next.of(styled, save)
                : changed(styled).and(save);
    }

    /**
     * Highlights the changed words of a line from now on, or stops to, keeping it for the next session. It only
     * changes how the diffs are drawn, so they are not read again. The plain style highlights no words, so there it
     * changes nothing: the popup shows it disabled.
     */
    private static Next wordsToggled(Model model) {
        Settings previous = model.preferences().settings();
        if (!previous.diffStyle().rich()) {
            return Next.of(model);
        }
        Settings chosen = previous.withDiffWords(!previous.diffWords());
        return Next.of(
                model.mapPreferences(preferences -> preferences.withSettings(chosen)),
                new SaveSettings(previous, chosen));
    }

    /**
     * Shows the changed lines of the rich view in pairs from now on (ADR 83), or as git gives them, keeping it for the
     * next session. It only changes how the diffs are drawn, so they are not read again. Only the unified rich style
     * lays lines out in pairs or not: the plain one shows them as git gives them, and the split one side by side, so
     * there it changes nothing and the popup shows it disabled.
     */
    private static Next pairsToggled(Model model) {
        Settings previous = model.preferences().settings();
        if (previous.diffStyle() != DiffStyle.RICH) {
            return Next.of(model);
        }
        Settings chosen = previous.withDiffPairs(!previous.diffPairs());
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
                .appendAll(RereadUpdate.shown(model).filter(load -> load instanceof DiffLoad<?>));
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
