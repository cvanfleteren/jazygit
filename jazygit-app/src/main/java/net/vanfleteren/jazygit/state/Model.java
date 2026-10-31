package net.vanfleteren.jazygit.state;

import io.vavr.collection.*;
import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.function.UnaryOperator;
import lombok.With;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.state.prs.PrOverview;

/**
 * The complete, immutable state of the repository as shown by the UI.
 *
 * @param repositoryName the name of the repository
 * @param screen         what the left of the screen shows
 * @param repo           what git reports about the repository
 * @param repoView       what the repository screen shows besides what git reports, and where its focus was
 * @param prOverview     what the pull requests screen shows, and where its focus was
 * @param gitHub         what GitHub reports about the branches of the repository
 * @param jobs           what runs in the background, and when the remotes were last fetched from
 * @param outcome        what the user is told about the operations: how the last one went, and the commands run
 * @param popup          the popup that is open, if any; only one can be
 * @param listCursor     where the user is in the list of the popup, if it has one
 * @param scrolled       where the areas of the content panel were scrolled to, by the id of the area
 * @param preferences    how the user wants things shown, kept from one session to the next or for this one only
 * @param now            the time the last message was handled at, which tells how long ago things happened
 */
@With
public record Model(
        String repositoryName,
        Screen screen,
        Repo repo,
        RepoView repoView,
        PrOverview prOverview,
        GitHubState gitHub,
        Jobs jobs,
        Outcome outcome,
        Option<Popup> popup,
        ListCursor listCursor,
        Map<String, Scrolled> scrolled,
        Preferences preferences,
        Instant now) {

    /**
     * How often the time shown moves on while the terminal does not have the focus: a frame every 5 s rather than
     * every second (ADR 67).
     */
    public static final Duration AWAY_CLOCK = Duration.ofSeconds(5);

    public static Model initial(String repositoryName) {
        return new Model(
                repositoryName,
                Screen.REPOSITORY,
                Repo.LOADING,
                RepoView.INITIAL,
                PrOverview.INITIAL,
                GitHubState.NOT_READ,
                Jobs.IDLE,
                Outcome.NONE,
                Option.none(),
                ListCursor.START,
                HashMap.empty(),
                Preferences.DEFAULT,
                Instant.EPOCH);
    }

    /**
     * The id of the element of the screen shown, outside the popups, that had the focus last, which a closing popup
     * gives it back to; none before one had it.
     */
    public Option<String> shownPane() {
        return switch (screen) {
            case REPOSITORY -> repoView.focusedPane();
            case PULL_REQUESTS -> prOverview.focusedPane();
        };
    }

    /**
     * This model with {@code id} as the element of the screen shown that had the focus last.
     *
     * @param content whether {@code id} is an area of the content panel, rather than a pane whose highlighted row
     *                it shows
     */
    public Model withShownPane(String id, boolean content) {
        return switch (screen) {
            // The files of a commit close when another pane gets the focus, as Escape closes them (ADR 44).
            case REPOSITORY ->
                withRepoView(repoView.withFocusedPane(Option.of(id))
                        .withCommitFiles(repoView.commitFiles()
                                .filter(view -> content || view.listedIn().equals(id))));
            case PULL_REQUESTS ->
                withPrOverview(prOverview
                        .withFocusedPane(Option.of(id))
                        .withListPane(content ? prOverview.listPane() : Option.of(id)));
        };
    }

    /**
     * Where the content area {@code area} is scrolled to while it shows {@code content}: where the user scrolled it
     * to, unless it showed something else then. When it showed the same changes read again, its cursor stays on its
     * stop, from the top, and its range is dropped (ADR 60).
     */
    public Scrolled scrolled(String area, ScrolledContent content) {
        Option<Scrolled> last = scrolled.get(area);
        return last.filter(at -> at.content().equals(content))
                .orElse(() -> last.filter(at -> at.content().sameChanges(content))
                        .map(at -> new Scrolled(content, 0, 0, at.cursor(), Option.none())))
                .getOrElse(() -> Scrolled.top(content));
    }

    /**
     * The open popup, if it is a {@code type}.
     */
    public <P extends Popup> Option<P> popup(Class<P> type) {
        return popup.filter(type::isInstance).map(type::cast);
    }

    /**
     * This model with {@code f} applied to what git reports about the repository.
     */
    public Model mapRepo(UnaryOperator<Repo> f) {
        return withRepo(f.apply(repo));
    }

    /**
     * This model with {@code f} applied to what the repository screen shows.
     */
    public Model mapRepoView(UnaryOperator<RepoView> f) {
        return withRepoView(f.apply(repoView));
    }

    /**
     * This model with {@code f} applied to what GitHub reports about the branches of the repository.
     */
    public Model mapGitHub(UnaryOperator<GitHubState> f) {
        return withGitHub(f.apply(gitHub));
    }

    /**
     * This model with {@code f} applied to what the user is told about the operations: how the last one went, and the commands run.
     */
    public Model mapOutcome(UnaryOperator<Outcome> f) {
        return withOutcome(f.apply(outcome));
    }

    /**
     * This model with {@code f} applied to what runs in the background, and when the remotes were last fetched from.
     */
    public Model mapJobs(UnaryOperator<Jobs> f) {
        return withJobs(f.apply(jobs));
    }

    /**
     * This model without what is kept only to start the loads, which no view draws: two models that draw the same
     * screen are equal once drawn. Only what is surely not drawn is left out; what is left out too little costs a
     * frame, what is left out too much would not be drawn when it changes.
     */
    public Model drawn() {
        // A command that runs shows only once it ran a while, which the spinners' frames tell, not a message, but for
        // the branch it keeps busy, which shows at once; the time shows to the second at most, and while the terminal
        // does not have the focus, to AWAY_CLOCK (ADR 67). What the timers left for later is not shown.
        return mapJobs(jobs -> jobs.withFailed(HashSet.empty())
                        .withFetchAgain(false)
                        .withWorktreeChangesRead(Option.none())
                        .withReadAt(HashMap.empty())
                        .withTicks(0)
                        .withRetries(HashMap.empty())
                        .withRunning(jobs.running()
                                .filterValues(command -> command.cmd() instanceof OperationCmd operation
                                        && operation.busyBranch().isDefined()))
                        .withNextId(0)
                        .withMissed(LinkedHashSet.empty())
                        .withAwayAt(HashMap.empty()))
                .withNow(jobs.away() ? awayClock(now) : now.truncatedTo(ChronoUnit.SECONDS));
    }

    private static Instant awayClock(Instant now) {
        long step = AWAY_CLOCK.toMillis();
        return Instant.ofEpochMilli(Math.floorDiv(now.toEpochMilli(), step) * step);
    }

    /**
     * This model with {@code f} applied to how the user wants things shown, kept from one session to the next or for this one only.
     */
    public Model mapPreferences(UnaryOperator<Preferences> f) {
        return withPreferences(f.apply(preferences));
    }

    /**
     * The local branch {@code name}, if it is one and no operation runs on it: a branch that is being pushed,
     * pulled or deleted takes no other operation meanwhile.
     */
    public Option<Branch> idleBranch(BranchName name) {
        return repo.branch(name).filter(known -> !jobs.busy().containsKey(new BranchRef.Local(name)));
    }

    /**
     * The commit {@code sha} of the commits pane: in the log of HEAD, or else among the results of the search of
     * it, which can find older commits than the log reads, or among the commits of HEAD that are not on its base.
     */
    public Option<Commit> commit(Sha sha) {
        return repo.commit(sha)
                .orElse(() -> repoView.commitSearch().flatMap(search -> search.commit(sha)))
                .orElse(() -> AheadUpdate.head(this).flatMap(ahead -> ahead.commit(sha)));
    }

    /**
     * This model without an error shown, nor a notice.
     */
    public Model clearError() {
        return mapOutcome(Outcome::cleared);
    }

    /**
     * This model showing {@code notice} in the command log, instead of an error.
     */
    public Model inform(Notice notice) {
        return mapOutcome(outcome -> outcome.informing(notice));
    }

    /**
     * This model showing {@code error}, in the command log and in an error popup, instead of a notice. A popup
     * that is open stays open instead, so what the user entered in it is not lost; the command log still shows the
     * error.
     */
    public Model fail(AppError error) {
        Model failed = mapOutcome(outcome -> outcome.failing(error));
        return popup.isDefined() ? failed : failed.openPopup(new ErrorPopup(error));
    }

    /**
     * This model with {@code popup} open, replacing the one that was open. A popup of the kind that was open, which
     * shows what changed in it, keeps where the user was in its list; any other one starts at the top.
     */
    public Model openPopup(Popup popup) {
        boolean same = this.popup.exists(open -> open.getClass() == popup.getClass());
        return withPopup(Option.of(popup)).withListCursor(same ? listCursor : ListCursor.START);
    }

    /**
     * This model with {@code popup} open, unless another one is: what comes up by itself, like the outcome of a
     * command, does not take away a dialog the user is typing in.
     */
    public Model openPopupIfFree(Popup popup) {
        return this.popup.isDefined() ? this : openPopup(popup);
    }

    /**
     * This model with the popup closed, if the open one is a {@code type}; a different popup that
     * opened in the meantime stays open.
     */
    public Model withoutPopup(Class<? extends Popup> type) {
        return popup(type).isDefined() ? withPopup(Option.none()).withListCursor(ListCursor.START) : this;
    }
}
