package net.vanfleteren.jazygit.feature.github.builds;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.gh.model.Build;
import net.vanfleteren.jazygit.state.popup.Popup;
import net.vanfleteren.jazygit.state.view.BuildsView.Pipeline;

/**
 * Asking which workflow's builds to show, of the many a branch has (ADR 131).
 *
 * @param choices the workflows, in the order their tabs show
 */
public record BuildsPopup(List<BuildsPopup.Choice> choices) implements Popup {

    /**
     * A workflow to choose.
     *
     * @param pipeline the workflow, and the event it runs for
     * @param label    how its tab names it
     * @param last     how its last build ended
     * @param builds   how many builds it has
     */
    public record Choice(Pipeline pipeline, String label, Build.Outcome last, int builds) {}
}
