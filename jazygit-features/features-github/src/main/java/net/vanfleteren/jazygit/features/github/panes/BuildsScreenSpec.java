package net.vanfleteren.jazygit.features.github.panes;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Duration;
import net.vanfleteren.jazygit.features.github.builds.screen.BuildsScreen;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.ui.screen.ScreenSpec;
import net.vanfleteren.jazygit.kernel.ui.text.ShownTime;
import net.vanfleteren.jazygit.kernel.ui.text.TimeText;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import net.vanfleteren.jazygit.kernel.ui.widgets.LoadableList;

/**
 * The builds screen, as GitHub's feature brings it (ADR 212): after its name, {@code ✗} while the last build on the
 * default branch of a workflow failed, and how many runs are in progress; when what it shows was read last.
 */
public final class BuildsScreenSpec {

    /**
     * The screen, as the UI draws it.
     */
    public static final ScreenSpec SPEC = new ScreenSpec(
            BuildsScreen.SCREEN,
            "github.screen.builds",
            BuildsScreenSpec::mark,
            model -> model.part(BuildsScreen.PART).freshness(),
            "github.status.read",
            BuildsScreenSpec::shownTimes);

    private BuildsScreenSpec() {}

    /**
     * {@code ✗} while the default branch fails, and {@code ●N} for the runs in progress, when there are any.
     */
    static Option<LoadableList.Seg> mark(Model model) {
        BuildsScreen screen = model.part(BuildsScreen.PART);
        boolean failing = screen.failingOnDefault();
        int running = screen.inProgress().getOrElse(0);
        String text = (failing ? " ✗" : "") + (running > 0 ? " ●" + running : "");
        return Option.when(
                !text.isEmpty(),
                () -> LoadableList.Seg.styled(
                        text, failing ? Theme.Classes.CHECKS_FAILED : Theme.Classes.CHECKS_RUNNING));
    }

    /**
     * The times the runs pane shows while the screen is shown (ADR 124): how long ago each run started.
     */
    static List<ShownTime> shownTimes(Model model) {
        return model.part(BuildsScreen.PART)
                .filtered()
                .getOrElse(List.empty())
                .map(run -> ShownTime.reading(now -> TimeText.age(Duration.between(run.createdAt(), now))));
    }
}
