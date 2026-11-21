package net.vanfleteren.jazygit.features.github.remarks;

import io.vavr.control.Option;
import java.util.Locale;
import net.vanfleteren.jazygit.core.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.core.gh.model.ReviewThread;
import net.vanfleteren.jazygit.kernel.state.diff.DiffSide;
import net.vanfleteren.jazygit.kernel.state.diff.Remark;
import net.vanfleteren.jazygit.kernel.state.diff.RemarkThread;
import net.vanfleteren.jazygit.kernel.state.diff.StatedRemarks;

/**
 * GitHub's review threads as the kernel's remark threads, and back (ADR 171): GitHub's node ids are the opaque ids of
 * the kernel, its sides of a diff the kernel's.
 */
public final class FromGitHub {

    private FromGitHub() {}

    /**
     * {@code thread} as the kernel places and draws it.
     */
    public static RemarkThread thread(ReviewThread thread) {
        return new RemarkThread(
                id(thread.id()),
                thread.path(),
                Option.none(),
                thread.subject() == ReviewThread.Subject.FILE ? RemarkThread.Subject.FILE : RemarkThread.Subject.LINE,
                thread.line(),
                side(thread.side()),
                thread.startLine(),
                thread.startLine().map(ignored -> side(thread.startSide())),
                thread.originalLine(),
                thread.outdated(),
                thread.resolved(),
                thread.more(),
                thread.comments().map(FromGitHub::remark));
    }

    /**
     * {@code comment} as the kernel draws it: why GitHub hides it as a word ({@code OFF_TOPIC} is off-topic), its
     * reactions as their emoji.
     */
    public static Remark remark(ReviewThread.Comment comment) {
        return new Remark(
                id(comment.id()),
                comment.author(),
                comment.at(),
                comment.body(),
                comment.hidden().map(reason -> reason.toLowerCase(Locale.ROOT).replace('_', '-')),
                comment.reactions()
                        .groups()
                        .map(group -> new Remark.Reaction(group.reaction().emoji(), group.count(), group.viewer())));
    }

    /**
     * The kernel's side of a diff that GitHub's {@code side} is.
     */
    public static DiffSide side(ReviewThread.Side side) {
        return side == ReviewThread.Side.LEFT ? DiffSide.OLD : DiffSide.NEW;
    }

    /**
     * GitHub's side of a diff that the kernel's {@code side} is.
     */
    public static ReviewThread.Side side(DiffSide side) {
        return side == DiffSide.OLD ? ReviewThread.Side.LEFT : ReviewThread.Side.RIGHT;
    }

    public static RemarkThread.Id id(ReviewThread.Id id) {
        return new RemarkThread.Id(id.value());
    }

    public static Remark.Id id(ReviewThread.Comment.Id id) {
        return new Remark.Id(id.value());
    }

    /**
     * The id of GitHub's thread the kernel's id {@code id} is.
     */
    public static ReviewThread.Id threadId(RemarkThread.Id id) {
        return new ReviewThread.Id(id.value());
    }

    /**
     * The id of GitHub's comment the kernel's id {@code id} is.
     */
    public static ReviewThread.Comment.Id commentId(Remark.Id id) {
        return new ReviewThread.Comment.Id(id.value());
    }

    /**
     * How a pull request in {@code state} ended, if it did.
     */
    public static Option<StatedRemarks.Ended> ended(PullRequestDetails.State state) {
        return switch (state) {
            case MERGED -> Option.of(StatedRemarks.Ended.MERGED);
            case CLOSED -> Option.of(StatedRemarks.Ended.CLOSED);
            case OPEN -> Option.none();
        };
    }
}
