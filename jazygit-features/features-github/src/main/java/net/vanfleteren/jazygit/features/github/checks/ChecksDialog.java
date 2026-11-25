package net.vanfleteren.jazygit.features.github.checks;

import static dev.tamboui.toolkit.Toolkit.text;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.StyledElement;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.core.gh.model.PullRequestDetails.Check;
import net.vanfleteren.jazygit.core.gh.timing.CheckSpan;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.features.github.failed.FailedBuilds;
import net.vanfleteren.jazygit.features.github.state.CheckTimes;
import net.vanfleteren.jazygit.features.github.state.GitHubState;
import net.vanfleteren.jazygit.features.github.state.Reading;
import net.vanfleteren.jazygit.features.github.widgets.CheckRows;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.build.FailedBuild;
import net.vanfleteren.jazygit.kernel.ui.checks.ChecksFrame;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import net.vanfleteren.jazygit.kernel.ui.text.ErrorText;
import net.vanfleteren.jazygit.kernel.ui.text.ShownTime;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import net.vanfleteren.jazygit.kernel.ui.widgets.PopupView;

/**
 * Popup listing the checks on the tip of a branch one by one (ADR 32), in the frame every forge lists them in (ADR
 * 195): how many are in each state and when they are expected to be done; when they started and ended, and how long
 * they took together (ADR 32); a bar of how far they are together (ADR 89); then each, the failed ones first, then the
 * running, the queued and the passed ones, with how long it took, or runs and is expected to take still; while one is
 * not done, with a bar of how far each is after its name, and how long it runs of how long it is expected to take. It
 * scrolls when they do not all fit. While they are read again, they stay shown, with a spinner in the title when the
 * user asked for that (ADR 34); when that fails, the title says they are stale.
 */
public final class ChecksDialog implements PopupView {

    public static final String ID = "checks";

    // How wide the bar of each check is, after its name, while one is not done.
    private static final int CHECK_BAR = 24;

    private final Clock clock;
    private final ChecksFrame<ChecksPopup> frame;

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public ChecksDialog(Consumer<Msg> dispatch) {
        this(dispatch, Clock.systemDefaultZone(), Keymap::standard);
    }

    /**
     * The popup with only its own keys, as without the app.
     *
     * @param clock animates the spinner, and its zone is the one times of day are shown in; how long checks run is
     *              told by the time of the model
     */
    ChecksDialog(Consumer<Msg> dispatch, Clock clock) {
        this(dispatch, clock, () -> ChecksAction.KEYS.apply(Keymap.empty()));
    }

    /**
     * @param keymap the keys of the popup, asked for when a key is pressed, as the app makes it of every feature
     */
    ChecksDialog(Consumer<Msg> dispatch, Clock clock, Supplier<Keymap> keymap) {
        this.clock = clock;
        this.frame = new ChecksFrame<>(ID, ChecksAction.SCOPE, dispatch, keymap, ChecksMsg.Closed::new);
    }

    @Override
    public List<String> ids() {
        return List.of(ID);
    }

    @Override
    public Option<Element> render(Model model) {
        return model.popup(ChecksPopup.class).map(popup -> {
            Option<CheckTimes> times = popup.times(model);
            return frame.render(
                    model,
                    popup,
                    title(popup, model.now()),
                    popup.checks()
                            .map(read -> read.map(checks -> listing(
                                    popup.checksRead(),
                                    times,
                                    popup.progress(model),
                                    popup.noLog(model)
                                            .map(ChecksDialog::noLog)
                                            .orElse(() -> popup.rerun().map(ChecksDialog::rerun)),
                                    popup.pickedCheck(model),
                                    model.now(),
                                    clock.getZone()))),
                    Messages.get("github.dialog.checks.loading"),
                    clock.instant(),
                    Messages.get("github.dialog.checks.hint"),
                    failedBuild(popup, model));
        });
    }

    /**
     * The times the dialog shows (ADR 124): every second while a check it lists is not done, as their bars and how
     * long they run move on.
     */
    public static List<ShownTime> shownTimes(Model model) {
        return model.popup(ChecksPopup.class)
                .filter(popup -> popup.checksRead().exists(check -> !check.done()))
                .map(popup -> ShownTime.every(Duration.ofSeconds(1)))
                .toList();
    }

    /**
     * Whether the dialog shows a spinner in {@code model}: while its checks are first read, or read again as the user
     * asked.
     */
    public static boolean showsSpinner(Model model) {
        return model.popup(ChecksPopup.class)
                .exists(popup -> popup.checks().isEmpty() || popup.reading() == Reading.ASKED);
    }

    /**
     * The branch, and when failed checks were asked to run again while that is awaited; then a spinner while its
     * checks are read again as the user asked, or a mark when a read failed.
     */
    private String title(ChecksPopup popup, Instant now) {
        String title = Messages.get("github.dialog.checks.title", name(popup.branch()))
                + popup.asked()
                        .filter(asked -> asked.awaited(now))
                        .map(asked -> " · "
                                + Messages.get(
                                        "github.dialog.checks.rerunAsked",
                                        ChecksFrame.timeOfDay(asked.at(), now, clock.getZone())))
                        .getOrElse("");
        return ChecksFrame.title(
                title, popup.reading() == Reading.ASKED, popup.refreshFailed().isDefined(), clock.instant());
    }

    private static String name(BranchRef branch) {
        return switch (branch) {
            case BranchRef.Local(BranchName name) -> name.value();
            case BranchRef.Remote(var remote) -> remote.shortName();
        };
    }

    /**
     * The counts, when they started and ended, how far they are, {@code progress}, what it says of the check picked or
     * of running failed checks again, {@code note}, and the checks, the one {@code picked} marked; a click on one picks
     * it. While one is not done, each has a bar of how far it is after its name.
     */
    private static ChecksFrame.Listing listing(
            List<Check> all,
            Option<CheckTimes> times,
            double progress,
            Option<StyledElement<?>> note,
            Option<Check> picked,
            Instant now,
            ZoneId zone) {
        List<Check> ordered = CheckRows.order(all, all, times);
        // The bars of the checks only while one is not done; once they all are, their names get the room.
        boolean running = all.exists(check -> !check.done());
        // Over all the checks, not only those shown, so the column keeps its width while they scroll.
        int doingWidth = running
                ? CheckRows.barredDoingWidth(ordered, all, times, now)
                : CheckRows.doingWidth(ordered, all, times, now, true);
        int width = ChecksFrame.ROW_WIDTH;
        return new ChecksFrame.Listing(
                CheckRows.counts(all, times, now),
                CheckSpan.of(all).map(span -> ChecksFrame.span(span.start(), span.end(), now, zone)),
                gauge(all, times, progress, now),
                note,
                ordered.map(check -> {
                    List<StyledElement<?>> drawn = running
                            ? CheckRows.barred(check, all, times, now, width, CHECK_BAR, doingWidth)
                            : CheckRows.filling(check, all, times, now, width, doingWidth, true);
                    boolean isPicked = picked.contains(check);
                    return new ChecksFrame.Row(
                            isPicked ? CheckRows.picked(check, drawn) : drawn,
                            isPicked,
                            new ChecksMsg.Clicked(CheckPick.of(check)));
                }));
    }

    /**
     * Why the check picked has no log (ADR 160).
     */
    static StyledElement<?> noLog(NoLog why) {
        return text(Messages.get(
                        switch (why) {
                            case NOT_DONE -> "github.dialog.checks.noLogNotDone";
                            case SKIPPED -> "github.dialog.checks.noLogSkipped";
                            case NOT_ACTIONS -> "github.dialog.checks.noLogNotActions";
                        }))
                .addClass(Theme.Classes.DIM);
    }

    /**
     * A bar of how far the checks {@code all} are together, {@code progress}, across the dialog, and how far that is:
     * {@code ━━━━━━━━━━━──────── 62%}. Red once one failed, green once they all passed, yellow while they take longer
     * than usual, and dimmed while when they are done is not known, as it then counts the checks done.
     */
    static List<StyledElement<?>> gauge(List<Check> all, Option<CheckTimes> times, double progress, Instant now) {
        return CheckRows.gauge(all, times, progress, now, ChecksFrame.CONTENT_WIDTH);
    }

    /**
     * What running failed checks again says (ADR 135): why nothing is, that GitHub is asked to, or how that went,
     * {@code rerun 2 of 3 · E2E: <what GitHub said>}, as an error when it refused one.
     */
    static StyledElement<?> rerun(RerunNote note) {
        return switch (note) {
            case RerunNote.Nothing(RerunPlan.Why why) -> text(nothing(why)).addClass(Theme.Classes.DIM);
            case RerunNote.Asked asked ->
                asked.outcomes()
                        .map(outcomes -> {
                            List<RerunCmd.Outcome> refused = asked.refused();
                            String text = List.of(Messages.get(
                                            "github.dialog.checks.rerunDone",
                                            outcomes.size() - refused.size(),
                                            outcomes.size()))
                                    .appendAll(
                                            refused.map(outcome -> outcome.run().workflow() + ": "
                                                    + outcome.refused()
                                                            .map(ErrorText::of)
                                                            .getOrElse("")))
                                    .mkString(" · ");
                            return text(text).addClass(refused.isEmpty() ? Theme.Classes.DIM : Theme.Classes.ERROR);
                        })
                        .getOrElse(() -> text(Messages.get(
                                        "github.dialog.checks.rerunAsking",
                                        asked.runIds().size()))
                                .addClass(Theme.Classes.DIM));
        };
    }

    private static String nothing(RerunPlan.Why why) {
        return Messages.get(
                switch (why) {
                    case NONE_FAILED -> "github.dialog.checks.rerunNoneFailed";
                    case RUNNING -> "github.dialog.checks.rerunRunning";
                    case NOT_ACTIONS -> "github.dialog.checks.rerunNotActions";
                });
    }

    /**
     * What failed of the checks {@code popup} shows in {@code model}, once they were read, for the keys other features
     * add to a failed build (ADR 161).
     */
    static Option<FailedBuild> failedBuild(ChecksPopup popup, Model model) {
        BranchName branch = switch (popup.branch()) {
            case BranchRef.Local(BranchName name) -> name;
            case BranchRef.Remote(var remote) -> follower(model, remote);
        };
        return popup.checks()
                .flatMap(read -> read.toOption())
                .map(read -> FailedBuilds.of(
                        branch,
                        read.repository(),
                        popup.checksRead(),
                        model.part(GitHubState.PART).pullRequest(popup.onGitHub())));
    }

    /**
     * The local branch whose build the checks of {@code remote} are: of the local branches that follow it, the one named
     * as it is, else the only one. Else its name on the remote, which no worktree has checked out, so the agent is not
     * started on a guess: several follow it once branches are made from it, as {@code git switch -c feature
     * origin/main}, until they are pushed; and a local branch of the same name may follow another remote.
     */
    private static BranchName follower(Model model, RemoteBranch remote) {
        List<Branch> followers = model.repo().followers(remote);
        return followers
                .find(branch -> branch.name().equals(remote.branch()))
                .orElse(() -> Option.when(followers.size() == 1, followers::head))
                .map(Branch::name)
                .getOrElse(() -> new BranchName(remote.shortName()));
    }
}
