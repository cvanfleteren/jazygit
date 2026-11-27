package net.vanfleteren.jazygit.features.forge.widgets;

import static dev.tamboui.toolkit.Toolkit.text;

import dev.tamboui.toolkit.element.StyledElement;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.features.forge.checks.ChecksPopup;
import net.vanfleteren.jazygit.features.forge.checks.ChecksPort;
import net.vanfleteren.jazygit.features.forge.checks.NoLog;
import net.vanfleteren.jazygit.features.forge.checks.Reading;
import net.vanfleteren.jazygit.features.forge.checks.RerunNote;
import net.vanfleteren.jazygit.features.forge.checks.RerunPlan;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.ui.checks.ChecksFrame;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.text.ErrorText;
import net.vanfleteren.jazygit.kernel.ui.text.ShownTime;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;

/**
 * What the checks dialog of every forge says alike around the checks it lists (ADR 209): its title, why the check
 * picked has no log, and how running failed checks again goes. Each forge lists the checks itself.
 */
public final class ChecksTexts {

    private ChecksTexts() {}

    /**
     * The open checks popup of {@code port} in {@code model}, which that forge's dialog draws.
     */
    public static Option<ChecksPopup> shown(Model model, ChecksPort port) {
        return model.popup(ChecksPopup.class).filter(popup -> popup.port().equals(port));
    }

    /**
     * The times the dialog of {@code port} shows (ADR 124): every second while a check it lists is not done, as how
     * long they run moves on.
     */
    public static List<ShownTime> shownTimes(Model model, ChecksPort port) {
        return shown(model, port)
                .filter(popup -> popup.checksRead().exists(check -> !check.done()))
                .map(popup -> ShownTime.every(Duration.ofSeconds(1)))
                .toList();
    }

    /**
     * Whether the dialog of {@code port} shows a spinner in {@code model}: while its checks are first read, or read
     * again as the user asked.
     */
    public static boolean showsSpinner(Model model, ChecksPort port) {
        return shown(model, port).exists(popup -> popup.checks().isEmpty() || popup.reading() == Reading.ASKED);
    }

    /**
     * The title of {@code popup} at {@code now}: the branch, and when failed checks were asked to run again while that
     * is awaited; then a spinner, turned by {@code clock}, while its checks are read again as the user asked, or a mark
     * when a read failed. Times of day are in the zone of {@code clock}.
     */
    public static String title(ChecksPopup popup, Instant now, Clock clock) {
        String title = Messages.get("forge.dialog.checks.title", name(popup.branch()))
                + popup.asked()
                        .filter(asked -> asked.awaited(now))
                        .map(asked -> " · "
                                + Messages.get(
                                        "forge.dialog.checks.rerunAsked",
                                        ChecksFrame.timeOfDay(asked.at(), now, clock.getZone())))
                        .getOrElse("");
        return ChecksFrame.title(
                title, popup.reading() == Reading.ASKED, popup.refreshFailed().isDefined(), clock.instant());
    }

    /**
     * What the popup says below its counts in {@code model}: why the check picked has no log, once asked, else of
     * running failed checks again; nothing before either.
     */
    public static Option<StyledElement<?>> note(ChecksPopup popup, Model model) {
        String forge = popup.port().forge();
        return popup.noLog(model)
                .<StyledElement<?>>map(why -> noLog(why, forge))
                .orElse(() -> popup.rerun().map(note -> rerun(note, forge)));
    }

    /**
     * What the dialog says while the checks are first read from {@code forge}.
     */
    public static String loading(String forge) {
        return Messages.get("forge.dialog.checks.loading", forge);
    }

    /**
     * The keys the dialog takes, below it.
     */
    public static String hint() {
        return Messages.get("forge.dialog.checks.hint");
    }

    /**
     * Why the check picked has no log on {@code forge} (ADR 160).
     */
    public static StyledElement<?> noLog(NoLog why, String forge) {
        return text(switch (why) {
                    case NOT_DONE -> Messages.get("forge.dialog.checks.noLogNotDone");
                    case SKIPPED -> Messages.get("forge.dialog.checks.noLogSkipped");
                    case NOT_ACTIONS -> Messages.get("forge.dialog.checks.noLogNotActions", forge);
                })
                .addClass(Theme.Classes.DIM);
    }

    /**
     * What running failed checks again says (ADR 135): why nothing is, that {@code forge} is asked to, or how that
     * went, {@code rerun 2 of 3 · E2E: <what the forge said>}, as an error when it refused one.
     */
    public static StyledElement<?> rerun(RerunNote note, String forge) {
        return switch (note) {
            case RerunNote.Nothing(RerunPlan.Why why) ->
                text(nothing(why, forge)).addClass(Theme.Classes.DIM);
            case RerunNote.Asked asked ->
                asked.outcomes()
                        .map(outcomes -> {
                            List<RerunPlan.Outcome> refused = asked.refused();
                            String text = List.of(Messages.get(
                                            "forge.dialog.checks.rerunDone",
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
                                        "forge.dialog.checks.rerunAsking",
                                        forge,
                                        asked.runs().size()))
                                .addClass(Theme.Classes.DIM));
        };
    }

    private static String nothing(RerunPlan.Why why, String forge) {
        return switch (why) {
            case NONE_FAILED -> Messages.get("forge.dialog.checks.rerunNoneFailed");
            case RUNNING -> Messages.get("forge.dialog.checks.rerunRunning");
            case NOT_ACTIONS -> Messages.get("forge.dialog.checks.rerunNotActions", forge);
        };
    }

    /**
     * The local branch whose build the checks of {@code popup} are in {@code model}, for the keys other features add
     * to a failed build (ADR 161): the one asked for, or of the local branches that follow the remote one asked for,
     * the one named as it is, else the only one. Else its name on the remote, which no worktree has checked out, so
     * the agent is not started on a guess: several follow it once branches are made from it, as {@code git switch -c
     * feature origin/main}, until they are pushed; and a local branch of the same name may follow another remote.
     */
    public static BranchName local(Model model, ChecksPopup popup) {
        return switch (popup.branch()) {
            case BranchRef.Local(BranchName name) -> name;
            case BranchRef.Remote(RemoteBranch remote) -> {
                List<Branch> followers = model.repo().followers(remote);
                yield followers
                        .find(branch -> branch.name().equals(remote.branch()))
                        .orElse(() -> Option.when(followers.size() == 1, followers::head))
                        .map(Branch::name)
                        .getOrElse(() -> new BranchName(remote.shortName()));
            }
        };
    }

    private static String name(BranchRef branch) {
        return switch (branch) {
            case BranchRef.Local(BranchName name) -> name.value();
            case BranchRef.Remote(var remote) -> remote.shortName();
        };
    }
}
