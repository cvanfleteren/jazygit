package net.vanfleteren.jazygit.content;

import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import io.vavr.Tuple;
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
import net.vanfleteren.jazygit.ui.i18n.Messages;
import net.vanfleteren.jazygit.ui.text.TextWidth;
import net.vanfleteren.jazygit.ui.theme.Theme;

/**
 * The jobs of a build as bars on one time line, each hanging off the jobs it waits for, as a merge train (ADR 131): a
 * line drops from the end of a job to an arrow before each job that needs it. The shards of a job, like
 * {@code acceptance (1/6)}, are one row, solid until the first shard ends and shaded until the last one does. Each
 * row says how long its job took, waited for a runner and took against its median (ADR 133).
 */
final class JobTrain {

    // The columns of how long a job took, like "1m28–2m23".
    private static final int TOOK = 10;
    // The columns of how long a job waited for a runner, and of how long it took against its median.
    private static final int WAITED = 8;
    private static final int VS = 11;
    // A difference from the median smaller than this is no change.
    private static final Duration STEADY = Duration.ofSeconds(10);
    // The narrowest the time line is.
    private static final int MIN_LINE = 10;

    private JobTrain() {}

    /**
     * The rows of the jobs of {@code build}, in a panel {@code panel} columns wide, under a line naming their columns;
     * {@code needs} says which jobs each job needs, empty when that is not known, and the jobs then show without lines
     * between them. Each row says how long its job took, how long it waited for a runner after what it needs ended,
     * and how long it took against {@code medians}, by the name of the job without its shard (ADR 133); a wait that
     * cannot be known, without the needs or with a job it needs that did not run, and a job still running against its
     * median, show {@code –}. The jobs that decided how long the build took, the last to end and what it waited for,
     * are in bold, once the needs are known and the build is done.
     */
    static List<Line> lines(
            Build build,
            Map<String, Set<String>> needs,
            Map<String, Duration> medians,
            int panel,
            Theme.SpanStyles styles) {
        List<Group> groups = groups(build, needs);
        if (groups.isEmpty()) {
            return List.empty();
        }
        Instant start = build.started();
        long whole = Math.max(
                1, Duration.between(start, groups.map(Group::end).max().get()).toMillis());
        // The names follow the same rule as those of the jobs over the builds.
        int name = BuildsText.names(
                groups.map(group -> TextWidth.of(group.label())).max().getOrElse(0), panel);
        // The time line takes what the names, its two borders and the numbers leave of the panel.
        int width = Math.max(MIN_LINE, panel - name - 2 - TOOK - WAITED - VS);
        Layout layout = layout(ordered(groups), start, whole, width);
        // Without the needs, the last job to end is all that is known: it did not decide alone. While the build runs,
        // nothing decided yet how long it takes.
        Set<String> critical =
                needs.isEmpty() || build.outcome() == Outcome.RUNNING ? HashSet.empty() : critical(groups);
        Line header = Line.from(Span.styled(
                " ".repeat(name + width + 2)
                        + padLeft(Messages.get("content.builds.train.took"), TOOK)
                        + padLeft(Messages.get("content.builds.train.waited"), WAITED)
                        + padLeft(Messages.get("content.builds.train.vsMedian"), VS),
                styles.of(Theme.Classes.DIM)));
        return layout.rows()
                .zipWithIndex()
                .map(row -> line(
                        layout,
                        row._1(),
                        row._2(),
                        name,
                        width,
                        new Numbers(
                                // A re-run keeps the jobs of the attempt before that passed, with their times: a
                                // wait after one of them would be the time between the attempts (ADR 133).
                                build.attempt() == 1 ? waited(row._1(), groups, start) : Option.none(),
                                medians.get(row._1().name()),
                                critical.contains(row._1().name())),
                        styles))
                .prepend(header);
    }

    /**
     * How long {@code group} waited for a runner: from the end of the last of the groups it needs, or from the start
     * of the build without any, to its start; none when what it needs is not known, or one it needs is not among
     * {@code groups}, as one skipped, whose end is not known.
     */
    static Option<Duration> waited(Group group, List<Group> groups, Instant start) {
        List<Group> needed = groups.filter(other -> group.needs().contains(other.name()));
        return Option.when(group.known() && needed.size() == group.needs().size(), () -> {
            Instant ready = needed.map(Group::end).max().getOrElse(start);
            Duration waited = Duration.between(ready, group.start());
            return waited.isNegative() ? Duration.ZERO : waited;
        });
    }

    /**
     * The names of the groups that decided how long the build took: the last to end, then of the groups it needs, the
     * last to end, and so on.
     */
    static Set<String> critical(List<Group> groups) {
        return HashSet.ofAll(List.<Option<Group>, String>unfoldRight(
                groups.maxBy(Comparator.comparing(Group::end)),
                at -> at.map(group -> Tuple.of(
                        group.name(),
                        groups.filter(other -> group.needs().contains(other.name()))
                                .maxBy(Comparator.comparing(Group::end))))));
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
            return new Group(name, members, needed, members.forAll(job -> needs.containsKey(job.name())));
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
            // A job it needs that did not run has no bar to wait for.
            List<Integer> from = group.needs()
                    .toList()
                    .filter(needed -> rows.exists(row -> row.name().equals(needed)))
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
    private static Map<Cell, Set<Direction>> connectors(List<Group> rows, List<Bar> bars, int width) {
        List<Segment> segments = rows.zipWithIndex((row, index) -> {
                    List<Integer> from = row.needs()
                            .toList()
                            .map(needed -> rows.indexWhere(other -> other.name().equals(needed)))
                            .filter(at -> at >= 0 && at < index);
                    if (from.isEmpty()) {
                        return List.<Segment>empty();
                    }
                    int arrow = bars.get(index).first() - 1;
                    List<Integer> columns =
                            from.map(at -> Math.min(width - 1, bars.get(at).end() + 1));
                    int leftmost = columns.min().get();
                    List<Segment> along = List.range(leftmost, arrow)
                            .flatMap(column -> List.of(
                                    new Segment(new Cell(index, column), Direction.RIGHT),
                                    new Segment(new Cell(index, column + 1), Direction.LEFT)));
                    List<Segment> down = from.zipWith(columns, Cell::new)
                            .flatMap(needed -> List.range(needed.row(), index)
                                    .flatMap(at -> List.of(
                                            new Segment(new Cell(at, needed.column()), Direction.DOWN),
                                            new Segment(new Cell(at + 1, needed.column()), Direction.UP)))
                                    .prepend(new Segment(needed, Direction.LEFT)));
                    return along.appendAll(down);
                })
                .flatMap(row -> row);
        return segments.groupBy(Segment::cell).mapValues(cell -> HashSet.ofAll(cell.map(Segment::direction)));
    }

    private static int column(Instant at, Instant start, long whole, int width) {
        // Three columns are kept free at the end: a bar starts after the line and the arrow before it, which can push
        // the last jobs a little later than their time.
        return (int) Math.max(0, Math.round(1.0 * Duration.between(start, at).toMillis() * (width - 4) / whole));
    }

    private static Line line(
            Layout layout, Group group, int index, int name, int width, Numbers numbers, Theme.SpanStyles styles) {
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
                    && layout.connectors().containsKey(new Cell(index, column))) {
                return Span.styled("▶", styles.of(Theme.Classes.DIM));
            }
            return layout.connectors()
                    .get(new Cell(index, column))
                    .map(directions -> Span.styled(Direction.box(directions), styles.of(Theme.Classes.DIM)))
                    .getOrElse(() -> Span.raw(" "));
        });
        Option<Duration> difference =
                group.slowest().flatMap(took -> numbers.median().map(took::minus));
        Span vsMedian = difference
                .map(change -> Span.styled(
                        padLeft(BuildsText.signed(change), VS),
                        styles.of(
                                change.compareTo(STEADY) > 0
                                        ? Theme.Classes.CHECKS_FAILED
                                        : change.compareTo(STEADY.negated()) < 0
                                                ? Theme.Classes.CHECKS_PASSED
                                                : Theme.Classes.DIM)))
                // A job still running has no time yet to hold against its median.
                .getOrElse(() -> group.members().exists(Build.Job::running)
                        ? Span.styled(padLeft("–", VS), styles.of(Theme.Classes.DIM))
                        : Span.raw(""));
        Span label = numbers.critical()
                ? Span.styled(pad(group.label(), name), styles.of(Theme.Classes.BOLD))
                : Span.raw(pad(group.label(), name));
        return Line.from(List.of(label, Span.styled("│", styles.of(Theme.Classes.DIM)))
                .appendAll(cells)
                .append(Span.styled("│", styles.of(Theme.Classes.DIM)))
                .append(Span.raw(padLeft(group.took(), TOOK)))
                .append(Span.styled(
                        padLeft(numbers.waited().map(BuildsText::duration).getOrElse("–"), WAITED),
                        styles.of(Theme.Classes.DIM)))
                .append(vsMedian)
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
     * @param needs   the names of the groups it needs, and of jobs it needs that did not run
     * @param known   whether what it needs is known: none of its members was left out of the needs
     */
    record Group(String name, List<Build.Job> members, Set<String> needs, boolean known) {

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
         * How long its slowest member took; none while one still runs.
         */
        Option<Duration> slowest() {
            return members.exists(Build.Job::running)
                    ? Option.none()
                    : members.flatMap(Build.Job::duration).max();
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
     * The numbers after a row's bar, besides how long it took.
     *
     * @param waited   how long it waited for a runner after what it needs ended; none when that is not known
     * @param median   how long it takes in the middle of the builds above; none when it never passed in them
     * @param critical whether it decided how long the build took
     */
    private record Numbers(Option<Duration> waited, Option<Duration> median, boolean critical) {}

    /**
     * The columns of a bar: where it starts, where its first shard ends, and where its last one does.
     */
    private record Bar(int first, int solid, int end) {}

    /**
     * The rows, their bars, and the lines between them by the cell they pass through.
     */
    private record Layout(List<Group> rows, List<Bar> bars, Map<Cell, Set<Direction>> connectors) {}

    /**
     * A cell of the train: the index of its {@code row} and its {@code column}.
     */
    private record Cell(int row, int column) {}

    /**
     * A piece of a line between the bars: the {@code cell} it passes through and the {@code direction} it leaves it
     * in.
     */
    private record Segment(Cell cell, Direction direction) {}

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
