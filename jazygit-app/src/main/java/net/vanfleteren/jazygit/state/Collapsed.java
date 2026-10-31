package net.vanfleteren.jazygit.state;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Set;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Comment;

/**
 * Which review threads and comments are collapsed (ADR 61): by default a resolved thread, a thread whose first comment
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
        boolean byDefault = thread.resolved()
                || thread.comments().headOption().exists(first -> first.hidden().isDefined());
        return byDefault != toggled.contains(thread.id());
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
     * These with the thread with the id {@code id} as it is by default.
     */
    public Collapsed reset(String id) {
        return new Collapsed(toggled.remove(id));
    }
}
