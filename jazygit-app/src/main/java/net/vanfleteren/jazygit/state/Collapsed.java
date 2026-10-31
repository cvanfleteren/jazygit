package net.vanfleteren.jazygit.state;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Comment;

/**
 * Which review threads and comments are collapsed (ADR 62): by default a resolved thread, a thread whose first comment
 * is hidden, and a hidden comment; else those {@code toggled} flips against their default. The first comment of a
 * thread is the thread: it is shown whole while the thread is open.
 *
 * @param toggled the ids of the threads and the comments collapsed or opened against their default
 */
public record Collapsed(Set<String> toggled) {

    /**
     * Each thread and comment as it is by default.
     */
    public static final Collapsed NONE = new Collapsed(HashSet.empty());

    /**
     * Whether {@code thread} is collapsed.
     */
    public boolean thread(ReviewThread thread) {
        return byDefault(thread) != toggled.contains(thread.id());
    }

    private static boolean byDefault(ReviewThread thread) {
        return thread.resolved()
                || thread.comments().headOption().exists(first -> first.hidden().isDefined());
    }

    /**
     * Whether {@code comment}, one of an open thread but its first, is collapsed.
     */
    public boolean comment(Comment comment) {
        return comment.hidden().isDefined() != toggled.contains(comment.id());
    }

    /**
     * The comments of {@code thread} that are a stop of the cursor of their own while it is open: all but its first,
     * which is the thread.
     */
    public static List<Comment> replies(ReviewThread thread) {
        return thread.comments().drop(1);
    }

    /**
     * These with the thread or the comment with the id {@code id} flipped.
     */
    public Collapsed flipped(String id) {
        return new Collapsed(toggled.contains(id) ? toggled.remove(id) : toggled.add(id));
    }

    /**
     * These once the threads read as {@code before} are read again as {@code after}: a thread or a comment whose
     * default changed, as a thread resolved or unresolved, or a comment hidden, is as such a one starts, not flipped
     * against its new default (ADR 62).
     */
    public Collapsed readAgain(List<ReviewThread> before, List<ReviewThread> after) {
        Map<String, Boolean> was = defaults(before);
        Set<String> changed = defaults(after)
                .filter((id, now) -> was.get(id).exists(then -> !then.equals(now)))
                .keySet();
        return new Collapsed(toggled.removeAll(changed));
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
