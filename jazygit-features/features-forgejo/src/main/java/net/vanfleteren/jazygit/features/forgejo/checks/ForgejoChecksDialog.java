package net.vanfleteren.jazygit.features.forgejo.checks;

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
import net.vanfleteren.jazygit.core.forgejo.CommitStatus;
import net.vanfleteren.jazygit.core.forgejo.ForgejoCheck;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.features.forgejo.pulls.ForgejoPullRequests;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.build.FailedBuild;
import net.vanfleteren.jazygit.kernel.ui.checks.CheckDurations;
import net.vanfleteren.jazygit.kernel.ui.checks.ChecksFrame;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import net.vanfleteren.jazygit.kernel.ui.text.ErrorText;
import net.vanfleteren.jazygit.kernel.ui.text.ShownTime;
import net.vanfleteren.jazygit.kernel.ui.text.TextWidth;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import net.vanfleteren.jazygit.kernel.ui.widgets.CheckSymbols;
import net.vanfleteren.jazygit.kernel.ui.widgets.PopupView;
import net.vanfleteren.jazygit.kernel.ui.widgets.Symbol;

/**
 * Popup listing the checks on the tip of a branch on Forgejo one by one (ADR 195), in the frame GitHub's are listed
 * in: how many are in each state; when they started and ended, and how long they took together; a bar of how far they
 * are together, counting those done, as Forgejo does not tell how long they take; then each, the failed ones first,
 * then the running, the queued and the passed ones, with how long it took, or has been running or queued. It scrolls
 * when they do not all fit. While they are read again, they stay shown, with a spinner in the title when the user
 * asked for that (ADR 34); when that fails, the title says they are stale.
 */
public final class ForgejoChecksDialog implements PopupView {

    public static final String ID = "forgejo-checks";

    private final Clock clock;
    private final ChecksFrame<ForgejoChecksPopup> frame;

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public ForgejoChecksDialog(Consumer<Msg> dispatch) {
        this(dispatch, Clock.systemDefaultZone(), Keymap::standard);
    }

    /**
     * @param clock  animates the spinner, and its zone is the one times of day are shown in; how long checks run is told
     *               by the time of the model
     * @param keymap the keys of the popup, asked for when a key is pressed, as the app makes it of every feature
     */
    ForgejoChecksDialog(Consumer<Msg> dispatch, Clock clock, Supplier<Keymap> keymap) {
        this.clock = clock;
        this.frame = new ChecksFrame<>(ID, ChecksAction.SCOPE, dispatch, keymap, ChecksMsg.Closed::new);
    }

    @Override
    public List<String> ids() {
        return List.of(ID);
    }

    @Override
    public Option<Element> render(Model model) {
        return model.popup(ForgejoChecksPopup.class)
                .map(popup -> frame.render(
                        model,
                        popup,
                        title(popup, model.now()),
                        popup.checks()
                                .map(read -> read.map(checks -> listing(
                                        checks,
                                        popup.noLog()
                                                .map(ForgejoChecksDialog::noLog)
                                                .orElse(() -> popup.rerun().map(ForgejoChecksDialog::rerun)),
                                        popup.pickedCheck(),
                                        model.now(),
                                        clock.getZone()))),
                        Messages.get("forgejo.dialog.checks.loading"),
                        clock.instant(),
                        Messages.get("forgejo.dialog.checks.hint"),
                        failedBuild(popup, model)));
    }

    /**
     * The times the dialog shows (ADR 124): every second while a check it lists is not done, as how long they run moves
     * on.
     */
    public static List<ShownTime> shownTimes(Model model) {
        return model.popup(ForgejoChecksPopup.class)
                .filter(popup -> popup.checksRead().exists(check -> !check.done()))
                .map(popup -> ShownTime.every(Duration.ofSeconds(1)))
                .toList();
    }

    /**
     * Whether the dialog shows a spinner in {@code model}: while its checks are first read, or read again as the user
     * asked.
     */
    public static boolean showsSpinner(Model model) {
        return model.popup(ForgejoChecksPopup.class)
                .exists(popup -> popup.checks().isEmpty() || popup.reading() == Reading.ASKED);
    }

    private String title(ForgejoChecksPopup popup, Instant now) {
        String title = Messages.get("forgejo.dialog.checks.title", name(popup.branch()))
                + popup.asked()
                        .filter(asked -> asked.awaited(now))
                        .map(asked -> " · "
                                + Messages.get(
                                        "forgejo.dialog.checks.rerunAsked",
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
     * The counts, when they started and ended, how far they are, {@code note}, and the checks, the one {@code picked}
     * marked; a click on one picks it.
     */
    static ChecksFrame.Listing listing(
            ChecksRead read, Option<StyledElement<?>> note, Option<ForgejoCheck> picked, Instant now, ZoneId zone) {
        List<ForgejoCheck> all = read.checks();
        List<ForgejoCheck> ordered = read.ordered();
        // As wide as the widest, and while one runs as wide as one that runs can get, so the names do not move.
        int doingWidth = ordered.map(check -> TextWidth.of(doing(check, now)))
                .appendAll(Option.when(all.exists(check -> !check.done()), () -> TextWidth.of(widestRunning())))
                .max()
                .getOrElse(0);
        return new ChecksFrame.Listing(
                CheckSymbols.counts(
                        all.count(check -> check.state() == CommitStatus.State.FAILED),
                        all.count(check -> !check.done()),
                        all.count(check -> check.state() == CommitStatus.State.PASSED)),
                span(all, now, zone),
                ChecksFrame.gauge(read.progress(), gauged(all)),
                note,
                ordered.map(check -> {
                    List<StyledElement<?>> drawn = ChecksFrame.filling(
                            List.of(symbol(check)),
                            check.name(),
                            check.queued(),
                            Option.of(doing(check, now)),
                            ChecksFrame.ROW_WIDTH,
                            doingWidth);
                    boolean isPicked = picked.contains(check);
                    return new ChecksFrame.Row(
                            isPicked ? ChecksFrame.picked(1, drawn) : drawn,
                            isPicked,
                            new ChecksMsg.Clicked(check.name()));
                }));
    }

    /**
     * When the checks {@code all} started, and once they are all done, ended; none while none has.
     */
    private static Option<StyledElement<?>> span(List<ForgejoCheck> all, Instant now, ZoneId zone) {
        Option<Instant> end = all.forAll(ForgejoCheck::done)
                ? all.flatMap(ForgejoCheck::ended).max()
                : Option.none();
        return all.flatMap(ForgejoCheck::started).min().map(start -> ChecksFrame.span(start, end, now, zone));
    }

    /**
     * The colour of the bar of how far the checks {@code all} are: red once one failed, green once they all passed,
     * dimmed while it counts those done, as when they are done is not known.
     */
    private static String gauged(List<ForgejoCheck> all) {
        return all.exists(check -> check.state() == CommitStatus.State.FAILED)
                ? Theme.Classes.CHECKS_FAILED
                : all.forAll(ForgejoCheck::done) ? Theme.Classes.CHECKS_PASSED : Theme.Classes.DIM;
    }

    private static Symbol symbol(ForgejoCheck check) {
        return switch (check.state()) {
            case FAILED -> CheckSymbols.FAILED;
            case PASSED -> CheckSymbols.PASSED;
            case RUNNING, NONE -> check.queued() ? CheckSymbols.QUEUED : CheckSymbols.RUNNING;
        };
    }

    /**
     * How {@code check} is doing at {@code now}: how long it took, {@code 2m}, {@code failed after 1m 30s},
     * {@code skipped}; or how long it has been running, {@code running 2m 5s}, or queued, {@code queued 40s}.
     */
    static String doing(ForgejoCheck check, Instant now) {
        if (check.skipped()) {
            return Messages.get("forgejo.checks.skipped");
        }
        Instant from = check.started().getOrElse(check.created());
        String took = CheckDurations.exact(Duration.between(from, check.ended().getOrElse(now)));
        if (check.done()) {
            return check.state() == CommitStatus.State.FAILED ? Messages.get("forgejo.checks.failedAfter", took) : took;
        }
        return check.queued()
                ? Messages.get("forgejo.checks.queuedFor", CheckDurations.exact(Duration.between(check.created(), now)))
                : Messages.get("forgejo.checks.runningFor", took);
    }

    /**
     * How a check that is not done says how it is doing at its widest, under an hour.
     */
    private static String widestRunning() {
        String widest = CheckDurations.exact(Duration.ofSeconds(59 * 60 + 59));
        String running = Messages.get("forgejo.checks.runningFor", widest);
        String queued = Messages.get("forgejo.checks.queuedFor", widest);
        return TextWidth.of(running) >= TextWidth.of(queued) ? running : queued;
    }

    /**
     * Why the check picked has no log (ADR 160).
     */
    static StyledElement<?> noLog(NoLog why) {
        return text(Messages.get(
                        switch (why) {
                            case NOT_DONE -> "forgejo.dialog.checks.noLogNotDone";
                            case SKIPPED -> "forgejo.dialog.checks.noLogSkipped";
                            case NOT_ACTIONS -> "forgejo.dialog.checks.noLogNotActions";
                        }))
                .addClass(Theme.Classes.DIM);
    }

    /**
     * What running failed checks again says (ADR 135): why nothing is, that Forgejo is asked to, or how that went,
     * {@code rerun 2 of 3 · ci: <what Forgejo said>}, as an error when it refused one.
     */
    static StyledElement<?> rerun(RerunNote note) {
        return switch (note) {
            case RerunNote.Nothing(RerunPlan.Why why) ->
                text(Messages.get(
                                switch (why) {
                                    case NONE_FAILED -> "forgejo.dialog.checks.rerunNoneFailed";
                                    case RUNNING -> "forgejo.dialog.checks.rerunRunning";
                                    case NOT_ACTIONS -> "forgejo.dialog.checks.rerunNotActions";
                                }))
                        .addClass(Theme.Classes.DIM);
            case RerunNote.Asked asked ->
                asked.outcomes()
                        .map(outcomes -> {
                            List<RerunCmd.Outcome> refused = asked.refused();
                            String text = List.of(Messages.get(
                                            "forgejo.dialog.checks.rerunDone",
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
                                        "forgejo.dialog.checks.rerunAsking",
                                        asked.runs().size()))
                                .addClass(Theme.Classes.DIM));
        };
    }

    /**
     * What failed of the checks {@code popup} shows in {@code model}, once they were read, for the keys other features
     * add to a failed build (ADR 161): the logs are where each status links to, as Forgejo's API reads a job's log
     * only by an id its links do not show.
     */
    static Option<FailedBuild> failedBuild(ForgejoChecksPopup popup, Model model) {
        BranchName branch = switch (popup.branch()) {
            case BranchRef.Local(BranchName name) -> name;
            case BranchRef.Remote(var remote) -> follower(model, remote);
        };
        return popup.read().map(read -> {
            List<ForgejoCheck> failed = read.checks().filter(check -> check.state() == CommitStatus.State.FAILED);
            return new FailedBuild(
                    Option.of(read.repository()),
                    branch,
                    Option.of(read.sha()),
                    model.part(ForgejoPullRequests.PART)
                            .open()
                            .flatMap(open -> open.get(popup.onForgejo()))
                            .map(pr -> new FailedBuild.ChangeRequest("pull request", (int) pr.number())),
                    failed.map(check -> new FailedBuild.Job(check.name(), check.url())),
                    read.checks().exists(check -> !check.done()),
                    failed.map(check -> check.name() + ": its log is on Forgejo"
                                    + check.url().map(url -> ", at " + url).getOrElse(""))
                            .mkString("\n"));
        });
    }

    /**
     * The local branch whose build the checks of {@code remote} are: of the local branches that follow it, the one
     * named as it is, else the only one; else its name on the remote, as GitHub's checks do.
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
