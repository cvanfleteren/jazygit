package net.vanfleteren.jazygit.feature.github.verdict;

import io.vavr.control.Option;
import lombok.With;
import net.vanfleteren.jazygit.feature.github.show.PullRequestPopup;
import net.vanfleteren.jazygit.feature.github.show.ShowsPullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Reviewing;
import net.vanfleteren.jazygit.gh.model.Verdict;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking for a review of a pull request, which approves it or asks for changes (ADR 99), from its dialog or from the
 * diff of its files. Only one popup is open at a time, so one from the dialog keeps the dialog to go back to; one from
 * the diff goes back to the diff.
 *
 * @param number      the number of the pull request
 * @param pullRequest its node id, which the review is added to
 * @param draft       whether it is still a draft
 * @param from        the popup it was opened from, as it is now, which it goes back to; none from the diff
 * @param verdict     what the review says; it starts out approving
 * @param pending     whether the viewer has a pending review on it, which stops this one being sent
 * @param text        the text of the review as far as it was typed, or as it came back from the editor
 * @param sending     whether it is being sent; it can then not be changed, nor sent again
 */
@With
public record VerdictPopup(
        int number,
        String pullRequest,
        boolean draft,
        Option<ShowsPullRequest> from,
        Verdict verdict,
        boolean pending,
        String text,
        boolean sending)
        implements Popup {

    /**
     * The popup asking for a review of the pull request {@code from} shows, which {@code reviewing} says how to.
     */
    static VerdictPopup of(ShowsPullRequest from, Reviewing reviewing) {
        return new VerdictPopup(
                from.number(),
                reviewing.pullRequest(),
                from.pullRequestPopup().draft().getOrElse(false),
                Option.of(from),
                Verdict.APPROVE,
                reviewing.pending(),
                "",
                false);
    }

    /**
     * The popup asking for a review of pull request {@code number} from the diff of its files, which {@code reviewing}
     * says how to.
     */
    static VerdictPopup ofDiff(int number, Reviewing reviewing, boolean draft) {
        return new VerdictPopup(
                number, reviewing.pullRequest(), draft, Option.none(), Verdict.APPROVE, reviewing.pending(), "", false);
    }

    /**
     * This popup going back to {@code dialog}, changed as it is now: a draft or not, and with a pending review of the
     * viewer or not, as its details say once read.
     */
    public VerdictPopup following(PullRequestPopup dialog) {
        return from.map(shown -> shown.withPullRequestPopup(dialog))
                .map(shown -> withFrom(Option.of(shown))
                        .withDraft(dialog.draft().getOrElse(draft))
                        .withPending(dialog.readDetails()
                                .map(read -> read.conversation().reviewing().pending())
                                .getOrElse(pending)))
                .getOrElse(this);
    }

    /**
     * The other verdict.
     */
    VerdictPopup switched() {
        return withVerdict(verdict == Verdict.APPROVE ? Verdict.REQUEST_CHANGES : Verdict.APPROVE);
    }

    @Override
    public boolean holdsInput() {
        return !text.isBlank();
    }
}
