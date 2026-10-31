package net.vanfleteren.jazygit.state;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Comment;

/**
 * Which review threads and comments are collapsed (ADR 62): by default a resolved thread, a thread whose first comment
 * is hidden, and a hidden comment; else those {@code toggled} flips against their default. The first comment of a
 * thread is the thread: it is shown whole while the thread is open.
 *
 * @param toggled the ids of the threads and the comments collapsed or opened against their default, each with whether
 *                that default was collapsed when it was flipped: one whose default is no longer that is as it starts,
 *                as one kept from an earlier session, of a thread resolved since (ADR 63)
 */
public record Collapsed(Map<String, Boolean> toggled) {

    /**
     * Each thread and comment as it is by default.
     */
    public static final Collapsed NONE = new Collapsed(HashMap.empty());

    /**
     * Whether {@code thread} is collapsed.
     */
    public boolean thread(ReviewThread thread) {
        return byDefault(thread) != isFlipped(thread.id(), byDefault(thread));
    }

    private static boolean byDefault(ReviewThread thread) {
        return thread.resolved()
                || thread.comments().headOption().exists(first -> first.hidden().isDefined());
    }

    /**
     * Whether {@code comment}, one of an open thread but its first, is collapsed.
     */
    public boolean comment(Comment comment) {
        return comment.hidden().isDefined()
                != isFlipped(comment.id(), comment.hidden().isDefined());
    }

    private boolean isFlipped(String id, boolean byDefault) {
        return toggled.get(id).contains(byDefault);
    }

    /**
     * The comments of {@code thread} that are a stop of the cursor of their own while it is open: all but its first,
     * which is the thread.
     */
    public static List<Comment> replies(ReviewThread thread) {
        return thread.comments().drop(1);
    }

    /**
     * These with the thread or the comment with the id {@code id}, one of {@code threads}, flipped; as they are when
     * it is none of them.
     */
    public Collapsed flipped(String id, List<ReviewThread> threads) {
        return defaults(threads)
                .get(id)
                .map(byDefault ->
                        new Collapsed(isFlipped(id, byDefault) ? toggled.remove(id) : toggled.put(id, byDefault)))
                .getOrElse(this);
    }

    /**
     * These once the threads are read again as {@code threads}: a thread or a comment whose default changed since it
     * was flipped, as a thread resolved or unresolved, or a comment hidden, is as such a one starts, not flipped
     * against its new default (ADR 62). One not read stays.
     */
    public Collapsed readAgain(List<ReviewThread> threads) {
        Map<String, Boolean> now = defaults(threads);
        return new Collapsed(toggled.filter((id, then) -> now.get(id).forAll(then::equals)));
    }

    /**
     * These with {@code kept} too, those kept from an earlier session (ADR 63): what was flipped in this one wins.
     */
    public Collapsed with(Map<String, Boolean> kept) {
        return new Collapsed(kept.merge(toggled, (before, now) -> now));
    }

    /**
     * Whether each of {@code threads}, and each of its comments that is a stop of its own, is collapsed by default, by
     * its id.
     */
    private static Map<String, Boolean> defaults(List<ReviewThread> threads) {
        return threads.flatMap(thread -> replies(thread)
                        .map(comment -> Tuple.of(comment.id(), comment.hidden().isDefined()))
                        .prepend(Tuple.of(thread.id(), byDefault(thread))))
                .toMap(Tuple2::_1, Tuple2::_2);
    }
}
