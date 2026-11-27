package net.vanfleteren.jazygit.features.github.panes;

import dev.tamboui.toolkit.elements.Panel;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.core.gh.model.Build;
import net.vanfleteren.jazygit.core.gh.model.RepoWorkflow;
import net.vanfleteren.jazygit.features.github.builds.screen.BuildsScopes;
import net.vanfleteren.jazygit.features.github.builds.screen.BuildsScreen;
import net.vanfleteren.jazygit.features.github.builds.screen.BuildsScreenMsg;
import net.vanfleteren.jazygit.kernel.state.Focus;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.PaneId;
import net.vanfleteren.jazygit.kernel.ui.ScreenPane;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import net.vanfleteren.jazygit.kernel.ui.screen.LeftPane;
import net.vanfleteren.jazygit.kernel.ui.text.TimeText;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import net.vanfleteren.jazygit.kernel.ui.widgets.LoadableList;
import net.vanfleteren.jazygit.kernel.ui.widgets.Pane;
import net.vanfleteren.jazygit.kernel.ui.widgets.PaneKeys;

/**
 * The pane of the workflows of the repository on the builds screen (ADR 212): how the last build of each on the
 * default branch ended and how long it took, its name, and how many builds it has among the commits of the default
 * branch read. It starts on the workflow the screen starts with.
 */
public final class BuildsWorkflowsPanel implements LeftPane {

    private final LoadableList<List<RepoWorkflow>> list;
    private final Keymap keymap = Keymap.standard();
    // The workflows last listed, so that the list is shown again only once they change.
    private Loadable<List<RepoWorkflow>> listed = Loadable.loading();
    // Whether the list was moved to the workflow the screen starts with.
    private boolean placed;

    private BuildsWorkflowsPanel(Consumer<Msg> dispatch, Consumer<PaneId> openContent, Theme.Highlights highlights) {
        list = new LoadableList<>(
                Messages.get(BuildsScopes.WORKFLOWS.titleKey()),
                BuildsScopes.WORKFLOWS.id(),
                LoadableList.Reselect.following(RepoWorkflow::name),
                highlights);
        list.whenEmpty(Messages.get("github.buildsScreen.noWorkflows"));
        list.onKey(event -> event.isConfirm()
                ? Option.of(() -> openContent.accept(BuildsScreen.WORKFLOWS_PANE))
                : PaneKeys.sending(keymap.handle(BuildsScopes.WORKFLOWS, event, selected()), dispatch));
    }

    /**
     * The pane, as the builds screen brings it.
     */
    public static LeftPane of(ScreenPane.Context context) {
        return new BuildsWorkflowsPanel(context.dispatch(), context.openContent(), context.highlights());
    }

    @Override
    public PaneId pane() {
        return BuildsScreen.WORKFLOWS_PANE;
    }

    @Override
    public Panel render(Model model, Option<Focus> focus, Option<PaneId> inUse) {
        BuildsScreen screen = model.part(BuildsScreen.PART);
        Loadable<List<RepoWorkflow>> now = screen.workflows().map(read -> screen.listed());
        if (!now.equals(listed)) {
            listed = now;
        }
        Panel panel = list.render(
                listed,
                read -> read.map(workflow -> row(screen, workflow)),
                focus.contains(pane()),
                inUse.contains(pane()),
                Pane.title(Messages.get(BuildsScopes.WORKFLOWS.titleKey())));
        if (!placed && screen.settled()) {
            listed.toOption().forEach(read -> {
                placed = true;
                screen.shown().map(read::indexOf).filter(index -> index >= 0).forEach(list::select);
            });
        }
        return panel;
    }

    @Override
    public Option<Msg> selectionChange(Model model) {
        return selected()
                .filter(workflow -> !model.part(BuildsScreen.PART).highlighted().contains(workflow.name()))
                .map(workflow -> new BuildsScreenMsg.WorkflowHighlighted(workflow.name()));
    }

    private Option<RepoWorkflow> selected() {
        return Option.when(placed, () -> list.selected(read -> read)).flatMap(found -> found);
    }

    /**
     * The row of {@code workflow}: how its last build on the default branch ended and how long it took, or a dot for
     * none among the commits read, its name, and how many builds it has there.
     */
    static LoadableList.Row row(BuildsScreen screen, RepoWorkflow workflow) {
        Option<Build> last = screen.lastOnDefault(workflow.name());
        int builds = screen.defaultBuilds().count(build -> build.workflow().equals(workflow.name()));
        LoadableList.Seg outcome = last.map(build -> RunRows.outcome(build.outcome()))
                .getOrElse(() -> LoadableList.Seg.styled("·", Theme.Classes.DIM));
        String took = last.flatMap(Build::duration).map(TimeText::took).getOrElse("");
        return new LoadableList.Row(List.of(
                outcome,
                LoadableList.Seg.of(" " + workflow.name()),
                LoadableList.Seg.styled(" " + took, Theme.Classes.DIM),
                LoadableList.Seg.styled(builds > 0 ? " " + builds : "", Theme.Classes.DIM)));
    }
}
