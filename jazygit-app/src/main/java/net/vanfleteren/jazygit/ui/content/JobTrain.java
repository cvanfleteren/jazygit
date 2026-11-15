package net.vanfleteren.jazygit.ui.content;

import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.Tuple3;
import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import net.vanfleteren.jazygit.gh.model.Build;
import net.vanfleteren.jazygit.gh.model.Build.Outcome;
import net.vanfleteren.jazygit.ui.text.TextWidth;
import net.vanfleteren.jazygit.ui.theme.Theme;

/**
 * The jobs of a build as bars on one time line, each hanging off the jobs it waits for, as a merge train (ADR 131): a
 * line drops from the end of a job to an arrow before each job that needs it. The shards of a job, like
 * {@code acceptance (1/6)}, are one row, solid until the first shard ends and shaded until the last one does.
 */
final class JobTrain {

    // The widest a job's name is shown.
    private static final int NAME = 28;

    private JobTrain() {}

    /**
     * The rows of the jobs of {@code build}, whose time line is {@code width} columns wide; {@code needs} says which
     * jobs each job needs, empty when that is not known, and the jobs then show without lines between them.
     */
    static List<Line> lines(Build build, Map<String, Set<String>> needs, int width, Theme.SpanStyles styles) {
        List<Group> groups = groups(build, needs);
        if (groups.isEmpty()) {
            return List.empty();
        }
        Instant start = build.started();
        long whole = Math.max(
                1, Duration.between(start, groups.map(Group::end).max().get()).toMillis());
        Layout layout = layout(ordered(groups), start, whole, width);
        int name = Math.min(
                        NAME,
                        groups.map(group -> TextWidth.of(group.label())).max().getOrElse(0))
                + 1;
        return layout.rows().zipWithIndex().map(row -> line(layout, row._1(), row._2(), name, width, styles));
    }

    /**
     * The jobs of {@code build} that ran, a job and its shards one group, and which groups each group needs.
     */
    static List<Group> groups(Build build, Map<String, Set<String>> needs) {
        List<Build.Job> ran = build.ran();
        List<String> names = ran.map(job -> BuildsText.jobName(job.name())).distinct();
        return names.map(name -> {
            List<Build.Job> members =
                    ran.filter(job -> BuildsText.jobName(job.name()).equals(name));
            Set<String> needed = HashSet.ofAll(
                            members.flatMap(job -> needs.get(job.name()).getOrElse(HashSet.empty()))
                                    .map(BuildsText::jobName))
                    .remove(name);
            return new Group(name, members, needed);
        });
    }

    /**
     * {@code groups} in the order the rows show them: a group after all it needs, as close under the one it needs that
     * came last as can be, so that the lines between them stay short; else by when it started.
     */
    static List<Group> ordered(List<Group> groups) {
        Set<String> known = HashSet.ofAll(groups.map(Group::name));
        return List.range(0, groups.size()).foldLeft(List.<Group>empty(), (placed, ignored) -> {
            List<String> names = placed.map(Group::name);
            List<Group> left = groups.filter(group -> !names.contains(group.name()));
            List<Group> ready =
                    left.filter(group -> group.needs().filter(known::contains).forAll(names::contains));
            Comparator<Group> closest = Comparator.<Group>comparingInt(
                            group -> -group.needs().map(names::indexOf).max().getOrElse(-1))
                    .thenComparing(Group::start)
                    .thenComparing(Group::end)
                    .thenComparing(Group::name);
            return placed.append((ready.isEmpty() ? left : ready).minBy(closest).get());
        });
    }

    /**
     * Where each of {@code rows} shows on a time line {@code width} columns wide from {@code start}, {@code whole}
     * milliseconds long: its bar, and the lines from the jobs it needs to an arrow before it. A bar starts two columns
     * after the latest end of those it needs at the earliest, which leaves room for the line and the arrow.
     */
    private static Layout layout(List<Group> rows, Instant start, long whole, int width) {
        List<Bar> bars = rows.foldLeft(List.<Bar>empty(), (done, group) -> {
            List<Integer> from = group.needs()
                    .toList()
                    .flatMap(needed -> rows.indexWhere(row -> row.name().equals(needed)) < done.size()
                            ? Option.of(
                                    done.get(rows.indexWhere(row -> row.name().equals(needed)))
                                                    .end()
                                            + 1)
                            : Option.none());
            int first = Math.min(
                    width - 1,
                    Math.max(
                            column(group.start(), start, whole, width),
                            from.max().map(at -> at + 2).getOrElse(0)));
            int solid = Math.max(first, Math.min(width - 1, column(group.firstEnd(), start, whole, width)));
            int end = Math.max(solid, Math.min(width - 1, column(group.end(), start, whole, width)));
            return done.append(new Bar(first, solid, end));
        });
        return new Layout(rows, bars, connectors(rows, bars, width));
    }

    /**
     * The lines between the bars, as the directions each cell they pass through connects to: down from the end of a
     * job, along the row of a job that needs it, to the arrow before its bar.
     */
    private static Map<Tuple2<Integer, Integer>, Set<Direction>> connectors(
            List<Group> rows, List<Bar> bars, int width) {
        List<Tuple3<Integer, Integer, Direction>> segments = rows.zipWithIndex().flatMap(row -> {
            int index = row._2();
            List<Integer> from = row._1()
                    .needs()
                    .toList()
                    .map(needed -> rows.indexWhere(other -> other.name().equals(needed)))
                    .filter(at -> at >= 0 && at < index);
            if (from.isEmpty()) {
                return List.empty();
            }
            int arrow = bars.get(index).first() - 1;
            List<Integer> columns =
                    from.map(at -> Math.min(width - 1, bars.get(at).end() + 1));
            int leftmost = columns.min().get();
            List<Tuple3<Integer, Integer, Direction>> along = List.range(leftmost, arrow)
                    .flatMap(column -> List.of(
                            Tuple.of(index, column, Direction.RIGHT), Tuple.of(index, column + 1, Direction.LEFT)));
            List<Tuple3<Integer, Integer, Direction>> down = from.zip(columns)
                    .flatMap(needed -> List.range(needed._1(), index)
                            .flatMap(at -> List.of(
                                    Tuple.of(at, needed._2(), Direction.DOWN),
                                    Tuple.of(at + 1, needed._2(), Direction.UP)))
                            .prepend(Tuple.of(needed._1(), needed._2(), Direction.LEFT)));
            return along.appendAll(down);
        });
        return segments.groupBy(segment -> Tuple.of(segment._1(), segment._2()))
                .mapValues(cell -> HashSet.ofAll(cell.map(Tuple3::_3)));
    }

    private static int column(Instant at, Instant start, long whole, int width) {
        // Three columns are kept free at the end: a bar starts after the line and the arrow before it, which can push
        // the last jobs a little later than their time.
        return (int) Math.max(0, Math.round(1.0 * Duration.between(start, at).toMillis() * (width - 4) / whole));
    }

    private static Line line(Layout layout, Group group, int index, int name, int width, Theme.SpanStyles styles) {
        Bar bar = layout.bars().get(index);
        String style = style(group.outcome());
        List<Span> cells = List.range(0, width).map(column -> {
            if (column >= bar.first() && column <= bar.solid()) {
                return Span.styled("█", styles.of(style));
            }
            if (column > bar.solid() && column <= bar.end()) {
                return Span.styled("▒", styles.of(style));
            }
            if (column == bar.first() - 1
                    && !group.needs().isEmpty()
                    && layout.connectors().containsKey(Tuple.of(index, column))) {
                return Span.styled("▶", styles.of(Theme.Classes.DIM));
            }
            return layout.connectors()
                    .get(Tuple.of(index, column))
                    .map(directions -> Span.styled(Direction.box(directions), styles.of(Theme.Classes.DIM)))
                    .getOrElse(() -> Span.raw(" "));
        });
        return Line.from(List.of(Span.raw(pad(group.label(), name)), Span.styled("│", styles.of(Theme.Classes.DIM)))
                .appendAll(cells)
                .append(Span.styled("│", styles.of(Theme.Classes.DIM)))
                .append(Span.raw(padLeft(group.took(), 10)))
                .toJavaList());
    }

    private static String style(Outcome outcome) {
        return switch (outcome) {
            case PASSED -> Theme.Classes.CHECKS_PASSED;
            case FAILED -> Theme.Classes.CHECKS_FAILED;
            case CANCELLED -> Theme.Classes.DIM;
            case RUNNING -> Theme.Classes.CHECKS_RUNNING;
        };
    }

    private static String pad(String text, int width) {
        String fitted = TextWidth.cut(text, width - 1);
        return fitted + " ".repeat(Math.max(0, width - TextWidth.of(fitted)));
    }

    private static String padLeft(String text, int width) {
        int shown = TextWidth.of(text);
        return shown >= width ? text : " ".repeat(width - shown) + text;
    }

    /**
     * A job and its shards, as one row.
     *
     * @param name    the job's name without its shard
     * @param members its runs: itself, or its shards
     * @param needs   the names of the groups it needs
     */
    record Group(String name, List<Build.Job> members, Set<String> needs) {

        /**
         * Its name, with how many shards it has, if it has more than one.
         */
        String label() {
            return members.size() > 1 ? name + " ×" + members.size() : name;
        }

        Instant start() {
            return members.flatMap(Build.Job::startedAt).min().get();
        }

        Instant firstEnd() {
            return members.map(job -> job.completedAt().getOrElse(start()))
                    .min()
                    .get();
        }

        Instant end() {
            return members.map(job -> job.completedAt().getOrElse(start()))
                    .max()
                    .get();
        }

        /**
         * How it ended: as a build of its members would.
         */
        Outcome outcome() {
            return new Build("", "", 0, 1, "", "", start(), members).outcome();
        }

        /**
         * How long it took: of one job, its time; of shards, the quickest and the slowest.
         */
        String took() {
            List<Duration> times = members.flatMap(Build.Job::duration);
            if (times.isEmpty()) {
                return "–";
            }
            String quickest = BuildsText.duration(times.min().get());
            String slowest = BuildsText.duration(times.max().get());
            return quickest.equals(slowest) ? slowest : quickest + "–" + slowest;
        }
    }

    /**
     * The columns of a bar: where it starts, where its first shard ends, and where its last one does.
     */
    private record Bar(int first, int solid, int end) {}

    /**
     * The rows, their bars, and the lines between them by the cell they pass through.
     */
    private record Layout(List<Group> rows, List<Bar> bars, Map<Tuple2<Integer, Integer>, Set<Direction>> connectors) {}

    /**
     * Which way a line leaves a cell.
     */
    enum Direction {
        UP,
        DOWN,
        LEFT,
        RIGHT;

        /**
         * The box drawing character that leaves a cell in {@code directions}, rounded where it turns.
         */
        static String box(Set<Direction> directions) {
            boolean up = directions.contains(UP);
            boolean down = directions.contains(DOWN);
            boolean left = directions.contains(LEFT);
            boolean right = directions.contains(RIGHT);
            return HashMap.of(
                            List.of(true, true, true, true), "┼",
                            List.of(true, true, false, true), "├",
                            List.of(true, true, true, false), "┤",
                            List.of(true, false, true, true), "┴",
                            List.of(false, true, true, true), "┬",
                            List.of(true, true, false, false), "│",
                            List.of(false, false, true, true), "─",
                            List.of(true, false, false, true), "╰",
                            List.of(false, true, false, true), "╭")
                    .put(List.of(true, false, true, false), "╯")
                    .put(List.of(false, true, true, false), "╮")
                    .get(List.of(up, down, left, right))
                    .getOrElse(up || down ? "│" : "─");
        }
    }
}
