package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import io.vavr.collection.Seq;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.ReviewThread;

/**
 * Where the cursor of a rich diff stops (ADR 60): what comes before its first file, then for each file its bar, the
 * threads about it, each line of its hunks that has a side and a number with the threads after it, and the threads that
 * cannot be placed; each comment of an open thread but its first is a stop of its own (ADR 62). A note, a hunk header
 * and a blank line are no stop: they are part of the stop before them. The rich view works out the stops as it draws
 * the diff; a key of the review needs only its lines ({@link DiffLines}).
 */
public final class DiffStops {

    private DiffStops() {}

    /**
     * The anchors of the stops of {@code thread}, after {@code line} if it is: the thread, then each of its comments
     * but the first while it is not {@code collapsed} (ADR 62).
     */
    public static List<Anchor> anchors(ReviewThread thread, Option<Anchor.OnLine> line, Collapsed collapsed) {
        Anchor.Thread anchor = new Anchor.Thread(thread.id(), line);
        return collapsed.thread(thread)
                ? List.of(anchor)
                : Collapsed.replies(thread)
                        .<Anchor>map(comment -> new Anchor.Comment(comment.id(), anchor))
                        .prepend(anchor);
    }

    /**
     * Which of {@code stops} the cursor on {@code at} is on (ADR 60): {@code at} itself if it is one of them, a thread
     * or a comment found by its id wherever it is now. When it is gone: a line goes to the line of the same file and
     * side with the closest number, else to the bar of its file, else to the first stop; a thread to the line it was
     * after, and from there as a line; a comment to its thread, and from there as a thread (ADR 62); a bar and the
     * lead to the first stop. None when there is no stop.
     */
    public static Option<Anchor> nearest(Seq<Anchor> stops, Anchor at) {
        Option<Anchor> found = switch (at) {
            case Anchor.Comment(String id, Anchor.Thread thread) ->
                stops.find(stop -> stop instanceof Anchor.Comment(String other, var ignored) && other.equals(id))
                        .orElse(() -> nearest(stops, thread));
            case Anchor.Thread(String id, Option<Anchor.OnLine> line) ->
                stops.find(stop -> stop instanceof Anchor.Thread(String other, var ignored) && other.equals(id))
                        .orElse(() -> line.flatMap(on -> nearest(stops, on)));
            case Anchor.OnLine(String path, ReviewThread.Side side, int number) ->
                stops.find(at::equals)
                        .orElse(() -> stops.filter(stop -> stop instanceof Anchor.OnLine(var p, var s, var ignored)
                                        && p.equals(path)
                                        && s == side)
                                .minBy(stop -> Math.abs(((Anchor.OnLine) stop).number() - number)))
                        .orElse(() -> stops.find(new Anchor.File(path)::equals));
            case Anchor.File file -> stops.find(file::equals);
            case Anchor.Lead lead -> stops.find(lead::equals);
        };
        return found.orElse(stops::headOption);
    }

    /**
     * A hunk of a diff: the index of its file, and its index in that file.
     */
    public record HunkAt(int file, int hunk) {}
}
