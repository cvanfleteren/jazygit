package net.vanfleteren.jazygit.features.github.builds.content;

import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.regex.Pattern;
import net.vanfleteren.jazygit.core.gh.model.Build;
import net.vanfleteren.jazygit.core.gh.model.Build.Outcome;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.github.builds.BuildsShown;
import net.vanfleteren.jazygit.features.github.builds.BuildsView.Pipeline;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.text.Placeholders;
import net.vanfleteren.jazygit.kernel.ui.text.TextWidth;
import net.vanfleteren.jazygit.kernel.ui.text.TimeText;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;

/**
 * The builds of a branch as the content panel draws them (ADR 131): for each workflow, a summary, a chart of how long
 * its builds took, how each job did, the jobs of its last build on one time line, and the builds one by one.
 */
final class BuildsText {

    // The rows of the chart, each eight steps high.
    private static final int CHART_ROWS = 8;
    // The builds a job's results and times are shown for.
    private static final int JOB_BUILDS = 40;
    // The columns before the chart: its scale.
    private static final int SCALE = 6;
    // The widest a job's name is shown in a narrow panel; a wider panel gives it up to a quarter of its width.
    private static final int NAME = 28;
    // The columns of a job's median, its last time and its trend, after its results and times.
    private static final int MEDIAN = 6;
    private static final int LAST = 7;
    private static final int TREND = 10;
    // A trend smaller than this is no change.
    private static final Duration STEADY = Duration.ofSeconds(10);
    private static final String EIGHTHS = " ▁▂▃▄▅▆▇█";
    private static final String SPARKS = "▁▂▃▄▅▆▇█";
    // What marks the build picked in the list, whose jobs show on one time line (ADR 133).
    private static final String PICKED = "▸";
    // What marks a run of the branch among those of every branch.
    private static final String MINE = "◆";
    // The widest the column of what a run on every branch ran for is: its pull request, else its branch.
    private static final int FOR = 14;
    // The widest a build's bar in the chart is, with the column after it, when there are few builds; also the widest
    // a build's cell in the results and the times of the jobs is.
    private static final int WIDEST = 5;
    // A job split in shards, like "acceptance (1/6)", is one job.
    private static final Pattern SHARD = Pattern.compile("\\s*\\(\\d+/\\d+\\)$");
    // The space between the columns of the list of builds.
    private static final String GAP = "  ";
    // A moment whose day has two digits in every zone, so that the column of when a build ran is as wide as it can
    // be. A month is as wide as December: in other locales the months can differ in width.
    private static final Instant WIDEST_DAY = Instant.parse("2000-12-15T12:00:00Z");

    private BuildsText() {}

    /**
     * The lines that draw what the builds of a branch show, {@code shown}, in {@code zone}, the chart as wide as
     * {@code width} allows: the tabs of their workflows, and the builds of the tab shown (ADR 131).
     */
    static List<Line> lines(BuildsShown shown, ZoneId zone, Theme.SpanStyles styles, int width) {
        Drawing drawing = new Drawing(
                styles,
                shown.needs(),
                shown.mine(),
                shown.allBranches(),
                shown.baseline(),
                shown.of(),
                shown.picked(),
                shown.onTimeLine(),
                DateTimeFormatter.ofPattern(Messages.get("github.builds.dayPattern"))
                        .withZone(zone),
                DateTimeFormatter.ofPattern(Messages.get("github.builds.whenPattern"))
                        .withZone(zone),
                width,
                Math.max(10, width - SCALE - 1));
        String workflow = shown.shown().workflow();
        List<Line> builds = switch (shown.builds()) {
            case Loadable.Loading<List<Build>>() ->
                List.of(Line.from(drawing.dim(Messages.get("github.builds.readingRuns", workflow))));
            case Loadable.Failed<List<Build>>(var failure) ->
                List.of(Line.from(Span.styled(Placeholders.error(failure), styles.of(Theme.Classes.ERROR))));
            case Loadable.Loaded<List<Build>>(var read) ->
                read.isEmpty()
                        ? List.of(Line.from(drawing.dim(Messages.get("github.builds.noRuns", workflow))))
                        : drawing.workflow(read);
        };
        return List.of(TabStrip.line(tabs(shown), shown.index(), width, styles), Line.from(""))
                .appendAll(builds);
    }

    /**
     * The workflow of the tab at {@code column} of the first line {@link #lines} draws of {@code shown},
     * {@code width} columns wide; none between the tabs.
     */
    static Option<Pipeline> tabAt(BuildsShown shown, int width, int column) {
        return TabStrip.at(tabs(shown), shown.index(), width, column).map(shown.tabs()::get);
    }

    /**
     * The run of the build at {@code row} and {@code column} of the lines {@link #lines} draws of {@code shown},
     * {@code width} columns wide: one in the list, or the column of one in the chart, or the marks under it; none
     * elsewhere (ADR 133).
     */
    static Option<Long> buildAt(BuildsShown shown, int width, int row, int column) {
        return shown.builds().toOption().filter(builds -> !builds.isEmpty()).flatMap(builds -> {
            List<Line> lines = lines(shown, ZoneOffset.UTC, Theme.SpanStyles.NONE, width);
            // The list of the builds ends the lines, a row each.
            int list = lines.size() - builds.size();
            if (row >= list && row < lines.size()) {
                return Option.of(builds.get(row - list).runId());
            }
            // The chart comes after the tabs, a blank line, the title, the summary and a blank line, with the axis,
            // the days and the marks under it.
            int chart = Math.max(10, width - SCALE - 1);
            List<Build> charted = builds.take(chart).reverse();
            int each = Math.max(1, Math.min(WIDEST, chart / charted.size()));
            int top = 5;
            boolean drawn = charted.exists(build -> build.duration().isDefined()
                    && (build.outcome() == Outcome.PASSED || build.outcome() == Outcome.FAILED));
            int at = (column - SCALE) / each;
            boolean onChart = row >= top && row < top + CHART_ROWS + 3 && column >= SCALE && at < charted.size();
            return Option.when(drawn && onChart, () -> charted.get(at).runId());
        });
    }

    private static List<TabStrip.Tab> tabs(BuildsShown shown) {
        return shown.tabs()
                .zip(shown.lasts())
                .map(tab -> new TabStrip.Tab(tab._1().label(shown.tabs()), tab._2()));
    }

    /**
     * {@code took} as the builds show it: {@code 45s}, {@code 4m08}, {@code 1h02}.
     */
    static String duration(Duration took) {
        return TimeText.took(took);
    }

    /**
     * {@code difference} with its sign: {@code +12s}, {@code −1m02}.
     */
    static String signed(Duration difference) {
        return (difference.isNegative() ? "−" : "+") + duration(difference.abs());
    }

    /**
     * The middle of {@code durations}; none without any.
     */
    static Option<Duration> median(List<Duration> durations) {
        List<Duration> sorted = durations.sorted();
        int size = sorted.size();
        if (size == 0) {
            return Option.none();
        }
        return Option.of(
                size % 2 == 1
                        ? sorted.get(size / 2)
                        : sorted.get(size / 2 - 1).plus(sorted.get(size / 2)).dividedBy(2));
    }

    /**
     * The duration {@code share} of {@code durations} are no longer than; none without any.
     */
    static Option<Duration> percentile(List<Duration> durations, double share) {
        List<Duration> sorted = durations.sorted();
        return Option.when(
                !sorted.isEmpty(), () -> sorted.get(Math.max(0, (int) Math.ceil(share * sorted.size()) - 1)));
    }

    /**
     * The job {@code name} is one of: its name without its shard.
     */
    static String jobName(String name) {
        return SHARD.matcher(name).replaceFirst("");
    }

    /**
     * How long the builds that passed took.
     */
    private static List<Duration> passed(List<Build> builds) {
        return builds.filter(build -> build.outcome() == Outcome.PASSED).flatMap(Build::duration);
    }

    /**
     * {@code text} in {@code width} columns, with at least one space after it, cut when it is wider.
     */
    private static String pad(String text, int width) {
        String fitted = TextWidth.cut(text, width - 1);
        return fitted + " ".repeat(Math.max(0, width - TextWidth.of(fitted)));
    }

    /**
     * {@code text} in at least {@code width} columns, never cut.
     */
    private static String padRight(String text, int width) {
        return text + " ".repeat(Math.max(0, width - TextWidth.of(text)));
    }

    /**
     * The widest of {@code values} and the header with the message {@code label}.
     */
    private static int widest(List<String> values, String label) {
        return values.map(TextWidth::of)
                .append(TextWidth.of(Messages.get(label)))
                .max()
                .getOrElse(0);
    }

    /**
     * How much longer than {@code median} {@code build} took, when it passed.
     */
    private static Option<Duration> difference(Build build, Option<Duration> median) {
        return build.duration()
                .filter(done -> build.outcome() == Outcome.PASSED)
                .flatMap(done -> median.map(done::minus));
    }

    private static String padLeft(String text, int width) {
        int shown = TextWidth.of(text);
        return shown >= width ? text : " ".repeat(width - shown) + text;
    }

    /**
     * The columns of each of {@code count} builds that share {@code series} columns, as even as can be.
     */
    static List<Integer> cells(int count, int series) {
        return List.range(0, count).map(index -> (index + 1) * series / count - index * series / count);
    }

    /**
     * The columns of the names of jobs, the widest {@code widest} columns wide, in a panel {@code panel} columns wide,
     * with the column after them: at most {@link #NAME}, or a quarter of a wider panel.
     */
    static int names(int widest, int panel) {
        return Math.min(Math.max(NAME, panel / 4), widest) + 1;
    }

    /**
     * The style of a time that changed by {@code change}: slower or faster by more than {@code steady}, or about the
     * same.
     */
    private static String trend(Duration change, Duration steady) {
        if (change.compareTo(steady) > 0) {
            return Theme.Classes.CHECKS_FAILED;
        }
        if (change.compareTo(steady.negated()) < 0) {
            return Theme.Classes.CHECKS_PASSED;
        }
        return Theme.Classes.DIM;
    }

    /**
     * The widths of the columns of the jobs, before their numbers.
     *
     * @param name   of the names of the jobs
     * @param passed of whether each passed
     * @param time   of how long each took
     * @param cells  of each build, in the results and the times
     */
    private record Columns(int name, int passed, int time, List<Integer> cells) {

        /**
         * The columns all builds take, in each of the results and the times.
         */
        int series() {
            return cells.sum().intValue();
        }
    }

    /**
     * The widths of the columns of the list of builds, each as wide as its widest value or its header; what a build
     * ran for only shows for the runs of a workflow on every branch.
     */
    private record ListColumns(int ranFor, int commit, int when, int time, int queued, int vsMedian) {}

    /**
     * How a job did in a build: how it ended, and how long it {@code took}, of its slowest shard.
     */
    private record JobRun(Outcome outcome, Duration took) {}

    /**
     * A build under the chart: the day it was on, whether that day starts at it, and the column it starts at.
     */
    private record DayMark(String day, boolean starts, int column) {}

    /**
     * How the builds are drawn.
     *
     * @param styles the styles of the theme
     * @param needs  which jobs each job needs, by the id of the run of a build whose jobs show on one time line
     * @param mine   the commits of the branch, whose runs are marked among those of every branch
     * @param allBranches whether the builds are the runs of their workflow on every branch
     * @param baseline how long the runs of the workflow that passed took on every branch
     * @param of     how many builds of the tab are read, of which some show
     * @param picked the run of the build whose jobs show on one time line; none for the last that is done
     * @param onTimeLine the build whose jobs show on one time line, as {@link BuildsShown#onTimeLine()} says
     * @param day    how a day is written
     * @param when   how a day and a time are written
     * @param width  the columns of the panel
     * @param chart  the builds the chart has room for
     */
    private record Drawing(
            Theme.SpanStyles styles,
            Map<Long, Map<String, Set<String>>> needs,
            Set<Sha> mine,
            boolean allBranches,
            Option<BuildsShown.Baseline> baseline,
            int of,
            Option<Long> picked,
            Option<Build> onTimeLine,
            DateTimeFormatter day,
            DateTimeFormatter when,
            int width,
            int chart) {

        /**
         * The lines that draw the builds of one workflow, the newest first.
         */
        List<Line> workflow(List<Build> builds) {
            Option<Duration> median = median(passed(builds));
            List<Line> above = List.of(title(builds), summary(builds, median), Line.from(""))
                    .appendAll(chart(builds.take(chart).reverse(), median))
                    .append(Line.from(""))
                    .appendAll(jobs(builds.take(JOB_BUILDS).reverse()))
                    .appendAll(timeLine(builds))
                    .append(Line.from(""));
            List<Line> all = above.appendAll(list(builds, median));
            // The row of the build picked is in the tint of the cursor of a diff, so it stands out among the others:
            // as far as the edge of the panel, or of the widest line when a long one lets the area scroll sideways.
            int widest = all.map(Line::width).append(width).max().getOrElse(width);
            int row = builds.indexWhere(build -> picked.contains(build.runId()));
            return row < 0
                    ? all
                    : all.update(
                            above.size() + 1 + row,
                            line -> line.append(Span.raw(" ".repeat(Math.max(0, widest - line.width()))))
                                    .patchStyle(styles.of(Theme.Classes.DIFF_CURSOR)));
        }

        private Span span(String text, String cssClass) {
            return Span.styled(text, styles.of(cssClass));
        }

        Span dim(String text) {
            return span(text, Theme.Classes.DIM);
        }

        private String symbol(Outcome outcome) {
            return switch (outcome) {
                case PASSED -> "✓";
                case FAILED -> "✗";
                case CANCELLED -> "⊘";
                case RUNNING -> "○";
            };
        }

        private String style(Outcome outcome) {
            return switch (outcome) {
                case PASSED -> Theme.Classes.CHECKS_PASSED;
                case FAILED -> Theme.Classes.CHECKS_FAILED;
                case CANCELLED -> Theme.Classes.DIM;
                case RUNNING -> Theme.Classes.CHECKS_RUNNING;
            };
        }

        private Span outcome(Outcome outcome) {
            return span(symbol(outcome), style(outcome));
        }

        private Line title(List<Build> builds) {
            Build newest = builds.head();
            String event = allBranches ? Messages.get("github.builds.allEvents") : newest.event();
            String from = day.format(builds.last().createdAt());
            String to = day.format(newest.createdAt());
            return Line.from(
                    span(newest.workflow(), Theme.Classes.BOLD),
                    dim(
                            of > builds.size()
                                    ? Messages.get(
                                            "github.builds.workflowOf",
                                            event,
                                            String.valueOf(builds.size()),
                                            String.valueOf(of),
                                            from,
                                            to)
                                    : Messages.get(
                                            "github.builds.workflow", event, String.valueOf(builds.size()), from, to)));
        }

        private Line summary(List<Build> builds, Option<Duration> median) {
            List<Duration> passed = passed(builds);
            List<Span> counts = List.of(Outcome.values())
                    .map(outcome -> Tuple.of(outcome, builds.count(build -> build.outcome() == outcome)))
                    .filter(counted -> counted._2() > 0 || counted._1() != Outcome.RUNNING)
                    .flatMap(counted ->
                            List.of(span(symbol(counted._1()) + " " + counted._2(), style(counted._1())), dim("  ")));
            Option<Build> last = builds.find(build -> build.duration().isDefined());
            List<Span> times = median.map(middle -> List.of(dim(Messages.get(
                            "github.builds.times",
                            String.valueOf(Math.round(100.0 * passed.size() / builds.size())),
                            duration(middle),
                            percentile(passed, 0.9).map(BuildsText::duration).getOrElse("")))))
                    .getOrElse(List.empty())
                    .appendAll(last.flatMap(build -> build.duration()
                                    .map(took -> List.of(
                                            dim("   " + Messages.get("github.builds.last", duration(took))),
                                            median.map(middle -> took.minus(middle))
                                                    .map(difference ->
                                                            span(" " + signed(difference), trend(difference, STEADY)))
                                                    .getOrElse(() -> dim("")))))
                            .getOrElse(List.empty()));
            List<Span> everyBranch = baseline.map(known -> List.of(dim(Messages.get(
                            "github.builds.baseline", duration(known.median()), String.valueOf(known.runs())))))
                    .getOrElse(List.empty());
            return Line.from(counts.appendAll(times).appendAll(everyBranch).toJavaList());
        }

        /**
         * The chart of how long {@code shown} took, the oldest first, one column each: a bar for a build that is
         * done and did not get cancelled, in the colour of how it ended; the median as a dotted line; under it, the
         * days and a mark for each build that did not pass.
         */
        private List<Line> chart(List<Build> shown, Option<Duration> median) {
            List<Option<Duration>> bars = shown.map(build -> build.duration()
                    .filter(took -> build.outcome() == Outcome.PASSED || build.outcome() == Outcome.FAILED));
            List<Duration> drawn = bars.flatMap(bar -> bar);
            if (drawn.isEmpty()) {
                return List.of(Line.from(dim(Messages.get("github.builds.noneDone"))));
            }
            long top = top(drawn);
            int medianRow = median.map(middle -> (int) (middle.toSeconds() * CHART_ROWS / top))
                    .getOrElse(-1);
            int each = Math.max(1, Math.min(WIDEST, chart / shown.size()));
            List<Line> rows = List.range(0, CHART_ROWS)
                    .reverse()
                    .map(row -> Line.from(List.of(dim(scale(row, top)))
                            .appendAll(shown.zip(bars)
                                    .flatMap(bar -> cell(bar._1(), bar._2(), row, top, row == medianRow, each)))
                            .toJavaList()));
            return rows.appendAll(axis(shown, each));
        }

        /**
         * The top of the chart of {@code drawn}, in seconds: no higher than the slowest build, nor than twice the
         * median, so that a few slow builds do not flatten the others; in whole steps, whose quarters are on
         * the scale.
         */
        private long top(List<Duration> drawn) {
            long highest = Math.min(
                    drawn.max().get().toSeconds(), 2 * median(drawn).get().toSeconds());
            long step = highest <= 120 ? 20 : highest <= 480 ? 60 : 120;
            return Math.max(step, (highest + step - 1) / step * step);
        }

        private String scale(int row, long top) {
            long seconds = top * (row + 1) / CHART_ROWS;
            // A whole number of minutes under an hour says only those: "8m", not "8m00".
            String label = seconds >= 60 && seconds < 3600 && seconds % 60 == 0
                    ? seconds / 60 + "m"
                    : duration(Duration.ofSeconds(seconds));
            return (row + 1) % 2 == 0 ? padLeft(label, SCALE - 1) + "┤" : " ".repeat(SCALE - 1) + "│";
        }

        /**
         * The cell of {@code build} in {@code row}, {@code each} columns wide: its bar, and a column between it and the
         * next once there is room for one.
         */
        private List<Span> cell(Build build, Option<Duration> bar, int row, long top, boolean medianRow, int each) {
            int eighths = bar.map(took -> (int) Math.round(took.toSeconds() * CHART_ROWS * 8.0 / top) - row * 8)
                    .getOrElse(0);
            boolean clipped = row == CHART_ROWS - 1 && bar.exists(took -> took.toSeconds() > top);
            String block =
                    clipped ? "▲" : eighths >= 8 ? "█" : eighths > 0 ? String.valueOf(EIGHTHS.charAt(eighths)) : " ";
            int wide = each > 2 ? each - 1 : each;
            Span drawn = block.equals(" ") && medianRow
                    ? dim("┄".repeat(wide))
                    : span(block.repeat(wide), style(build.outcome()));
            return each > 2 ? List.of(drawn, medianRow ? dim("┄") : Span.raw(" ")) : List.of(drawn);
        }

        /**
         * The axis under the chart, the days the builds were on, and a mark for each build that did not pass.
         */
        private List<Line> axis(List<Build> shown, int each) {
            List<String> days = shown.map(build -> day.format(build.createdAt()));
            // A day starts at a build on another day than the one before it.
            List<Boolean> starts = days.zip(days.map(Option::some).prepend(Option.none()))
                    .map(pair -> !pair._2().contains(pair._1()));
            String labels = days.zip(starts)
                    .zipWithIndex()
                    .map(dated -> new DayMark(dated._1()._1(), dated._1()._2(), dated._2() * each))
                    .foldLeft(
                            "",
                            (done, mark) -> mark.starts() && done.length() <= mark.column()
                                    ? done + " ".repeat(mark.column() - done.length()) + mark.day() + " "
                                    : done);
            return List.of(
                            Line.from(dim(" ".repeat(SCALE - 1) + "└"
                                    + starts.map(start -> (start ? "┴" : "─") + "─".repeat(each - 1))
                                            .mkString())),
                            Line.from(dim(" ".repeat(SCALE) + labels.stripTrailing())),
                            Line.from(List.of(dim(" ".repeat(SCALE)))
                                    .appendAll(shown.flatMap(build -> List.of(
                                            build.outcome() == Outcome.PASSED
                                                    ? Span.raw(" ")
                                                    : outcome(build.outcome()),
                                            Span.raw(" ".repeat(each - 1)))))
                                    .toJavaList()))
                    .appendAll(Option.when(
                            allBranches && shown.exists(this::isMine),
                            () -> Line.from(List.of(dim(" ".repeat(SCALE)))
                                    .appendAll(shown.flatMap(build -> List.of(
                                            isMine(build) ? span(MINE, Theme.Classes.CURRENT_BRANCH) : Span.raw(" "),
                                            Span.raw(" ".repeat(each - 1)))))
                                    .append(dim(Messages.get("github.builds.mine")))
                                    .toJavaList())));
        }

        /**
         * Whether {@code build} ran on a commit of the branch.
         */
        private boolean isMine(Build build) {
            return mine.contains(build.sha());
        }

        /**
         * What a run on every branch ran for: its pull request, else its branch.
         */
        private String ranFor(Build build) {
            return build.origin()
                    .pullRequest()
                    .map(number -> "#" + number.value())
                    .orElse(build.origin().branch().map(BranchName::value))
                    .getOrElse("–");
        }

        /**
         * How each job did over {@code builds}, the oldest first: whether it passed and how long it took, its median,
         * its last time, and its trend, the median of the newer half against that of the older half.
         */
        private List<Line> jobs(List<Build> builds) {
            List<String> names = builds.flatMap(build -> build.ran()
                            .map(job -> Tuple.of(
                                    jobName(job.name()),
                                    job.startedAt()
                                            .map(start -> Duration.between(build.started(), start))
                                            .getOrElse(Duration.ZERO))))
                    .groupBy(Tuple2::_1)
                    .toList()
                    .map(named -> Tuple.of(
                            named._1(), median(named._2().map(Tuple2::_2)).getOrElse(Duration.ZERO)))
                    .sorted(Comparator.<Tuple2<String, Duration>, Duration>comparing(Tuple2::_2)
                            .thenComparing(Tuple2::_1))
                    .map(Tuple2::_1);
            if (names.isEmpty()) {
                return List.empty();
            }
            int nameColumns = names(names.map(TextWidth::of).max().getOrElse(0), width);
            int count = builds.size();
            String passedLabel = Messages.get("github.builds.lastBuilds", String.valueOf(count));
            String timeLabel = Messages.get("github.builds.time");
            // The results and the times share what the names and the numbers leave of the panel: each build gets a
            // column at least, and no more than WIDEST.
            int series =
                    Math.max(count, Math.min(count * WIDEST, (width - nameColumns - MEDIAN - LAST - TREND - 4) / 2));
            // A column is as wide as its builds, or as its header with few of them, with two columns after it.
            Columns columns = new Columns(
                    nameColumns,
                    Math.max(series, TextWidth.of(passedLabel)) + 2,
                    Math.max(series, TextWidth.of(timeLabel)) + 2,
                    cells(count, series));
            Line header = Line.from(dim(pad(Messages.get("github.builds.job"), nameColumns)
                    + pad(passedLabel, columns.passed())
                    + pad(timeLabel, columns.time())
                    + Messages.get("github.builds.jobTimes")));
            return names.map(name -> job(name, builds.map(build -> ofJob(build, name)), columns))
                    .prepend(header)
                    .append(Line.from(""));
        }

        /**
         * How the job {@code name} did in {@code build}: how it ended and how long it took, of its slowest shard; none
         * when it did not run.
         */
        private Option<JobRun> ofJob(Build build, String name) {
            List<Build.Job> shards =
                    build.ran().filter(job -> jobName(job.name()).equals(name));
            return Option.when(
                    !shards.isEmpty(),
                    () -> new JobRun(
                            Outcome.of(shards),
                            shards.flatMap(Build.Job::duration).max().getOrElse(Duration.ZERO)));
        }

        private Line job(String name, List<Option<JobRun>> runs, Columns columns) {
            List<Duration> passed = runs.flatMap(run -> run)
                    .filter(run -> run.outcome() == Outcome.PASSED)
                    .map(JobRun::took);
            Duration low = passed.min().getOrElse(Duration.ZERO);
            Duration high = passed.max().getOrElse(Duration.ZERO);
            List<Span> results = runs.zip(columns.cells())
                    .map(run -> run._1()
                            .map(done -> switch (done.outcome()) {
                                case PASSED -> span("▆".repeat(run._2()), Theme.Classes.CHECKS_PASSED);
                                case FAILED -> span("▆".repeat(run._2()), Theme.Classes.CHECKS_FAILED);
                                case CANCELLED, RUNNING -> dim("▁".repeat(run._2()));
                            })
                            .getOrElse(() -> Span.raw(" ".repeat(run._2()))));
            List<Span> sparks = runs.zip(columns.cells())
                    .map(run -> run._1()
                            .map(done -> done.outcome() == Outcome.PASSED
                                    ? Span.raw(String.valueOf(SPARKS.charAt(
                                                    high.equals(low)
                                                            ? 0
                                                            : (int) Math.round(7.0
                                                                    * done.took()
                                                                            .minus(low)
                                                                            .toMillis()
                                                                    / high.minus(low)
                                                                            .toMillis())))
                                            .repeat(run._2()))
                                    : done.outcome() == Outcome.FAILED
                                            ? span("▁".repeat(run._2()), Theme.Classes.CHECKS_FAILED)
                                            : Span.raw(" ".repeat(run._2())))
                            .getOrElse(() -> Span.raw(" ".repeat(run._2()))));
            int half = passed.size() / 2;
            Option<Duration> trend = Option.when(half > 0, half)
                    .flatMap(older -> median(passed.drop(older))
                            .flatMap(newer -> median(passed.take(older)).map(newer::minus)));
            Option<Duration> last =
                    runs.reverse().flatMap(run -> run).headOption().map(JobRun::took);
            return Line.from(List.of(Span.raw(pad(name, columns.name())))
                    .appendAll(results)
                    .append(Span.raw(" ".repeat(Math.max(0, columns.passed() - columns.series()))))
                    .appendAll(sparks)
                    .append(Span.raw(" ".repeat(Math.max(0, columns.time() - columns.series()))))
                    .append(Span.raw(
                            padLeft(median(passed).map(BuildsText::duration).getOrElse("–"), MEDIAN)))
                    .append(Span.raw(padLeft(last.map(BuildsText::duration).getOrElse("–"), LAST)))
                    .append(trend.map(change -> change.compareTo(STEADY) > 0
                                    ? span("  ▲ " + signed(change), Theme.Classes.CHECKS_FAILED)
                                    : change.compareTo(STEADY.negated()) < 0
                                            ? span("  ▼ " + signed(change), Theme.Classes.CHECKS_PASSED)
                                            : dim("    " + signed(change)))
                            .getOrElse(() -> Span.raw("")))
                    .toJavaList());
        }

        /**
         * The jobs of the build picked, else of the last build that is done, on one time line, each hanging off the
         * jobs it waits for (ADR 131): with how long it waited for a runner, and how long it took against its median
         * over the builds of the jobs above (ADR 133).
         */
        private List<Line> timeLine(List<Build> builds) {
            return onTimeLine
                    .map(build -> {
                        boolean chosen = picked.contains(build.runId());
                        Line header = Line.from(List.of(
                                        outcome(build.outcome()),
                                        Span.raw(" "),
                                        span(build.sha().shortSha(), Theme.Classes.SHA),
                                        Span.raw(" " + build.title()),
                                        dim(Messages.get(
                                                chosen ? "github.builds.pickedBuild" : "github.builds.lastBuild",
                                                build.duration()
                                                        .map(BuildsText::duration)
                                                        .getOrElse("–"),
                                                when.format(build.createdAt()))))
                                .appendAll(build.queued()
                                        .map(queued -> dim(Messages.get("github.builds.queued", duration(queued))))
                                        .toList())
                                .toJavaList());
                        Map<String, Set<String>> ofBuild =
                                needs.get(build.runId()).getOrElse(HashMap.empty());
                        return JobTrain.lines(
                                        build, ofBuild, jobMedians(builds.take(JOB_BUILDS)), chart + SCALE + 1, styles)
                                .prepend(header);
                    })
                    .getOrElse(List.empty());
        }

        /**
         * Of each job of {@code builds}, by its name without its shard, how long it took in the middle of the builds it
         * passed in, of its slowest shard: as the jobs above show it.
         */
        private Map<String, Duration> jobMedians(List<Build> builds) {
            return HashMap.ofEntries(builds.flatMap(build -> build.ran().map(job -> jobName(job.name())))
                    .distinct()
                    .flatMap(name -> median(builds.flatMap(build -> ofJob(build, name))
                                    .filter(run -> run.outcome() == Outcome.PASSED)
                                    .map(JobRun::took))
                            .map(middle -> Tuple.of(name, middle))));
        }

        /**
         * The builds one by one, the newest first: how it ended, its commit, when, how long it took, how long of that
         * it waited for its first job to start, against the median, and the commit's title.
         */
        private List<Line> list(List<Build> builds, Option<Duration> median) {
            ListColumns columns = listColumns(builds, median);
            return builds.map(build -> row(build, difference(build, median), columns))
                    .prepend(header(columns));
        }

        /**
         * The widths of the columns of the list of {@code builds}, each as wide as its widest value or its header.
         */
        private ListColumns listColumns(List<Build> builds, Option<Duration> median) {
            return new ListColumns(
                    // As wide as what the builds ran for and its header, with a column after it, up to FOR.
                    Math.min(
                            FOR,
                            builds.map(build -> TextWidth.of(ranFor(build)))
                                            .append(TextWidth.of(Messages.get("github.builds.for")))
                                            .max()
                                            .getOrElse(0)
                                    + 1),
                    widest(builds.map(build -> build.sha().shortSha()), "github.builds.commit"),
                    // A day of two digits is reserved for, so that the column stays put as older builds are read.
                    widest(
                            builds.map(build -> when.format(build.createdAt())).append(when.format(WIDEST_DAY)),
                            "github.builds.when"),
                    // A time below an hour is reserved for, so that the column stays put across workflows too.
                    widest(
                            builds.map(build -> build.duration()
                                            .map(BuildsText::duration)
                                            .getOrElse("–"))
                                    .append(duration(Duration.ofMinutes(59))),
                            "github.builds.time"),
                    // As wide as its header, which is wider than a wait below an hour.
                    widest(
                            builds.flatMap(build -> build.queued().map(BuildsText::duration)),
                            "github.builds.queuedHeader"),
                    widest(
                            builds.flatMap(build -> difference(build, median)).map(BuildsText::signed),
                            "github.builds.vsMedian"));
        }

        /**
         * The header of the list of builds, over its {@code columns}.
         */
        private Line header(ListColumns columns) {
            return Line.from(dim("  "
                    + (allBranches ? "  " + pad(Messages.get("github.builds.for"), columns.ranFor()) : "")
                    + padRight(Messages.get("github.builds.commit"), columns.commit())
                    + GAP
                    + padRight(Messages.get("github.builds.when"), columns.when())
                    + GAP
                    + padLeft(Messages.get("github.builds.time"), columns.time())
                    + GAP
                    + padLeft(Messages.get("github.builds.queuedHeader"), columns.queued())
                    + GAP
                    + padLeft(Messages.get("github.builds.vsMedian"), columns.vsMedian())
                    + GAP
                    + Messages.get("github.builds.title")));
        }

        /**
         * The row of {@code build} in the list of builds, which took {@code difference} longer than their median.
         */
        private Line row(Build build, Option<Duration> difference, ListColumns columns) {
            // The build picked is marked after how it ended, and its title is in bold: a mark at the end of its row
            // would be cut off with a long title (ADR 133). Its row is tinted after.
            boolean chosen = picked.contains(build.runId());
            return Line.from(List.of(
                            outcome(build.outcome()), chosen ? span(PICKED, Theme.Classes.BOLD) : Span.raw(" "))
                    .appendAll(forWhat(build, columns))
                    .appendAll(List.of(
                            span(padRight(build.sha().shortSha(), columns.commit()), Theme.Classes.SHA),
                            dim(GAP + padLeft(when.format(build.createdAt()), columns.when()) + GAP),
                            Span.raw(padLeft(
                                    build.duration().map(BuildsText::duration).getOrElse("–"), columns.time())),
                            dim(GAP
                                    + padLeft(
                                            build.queued()
                                                    .map(BuildsText::duration)
                                                    .getOrElse(build.attempt() == 1 ? "–" : ""),
                                            columns.queued())
                                    + GAP),
                            vsMedian(difference, columns.vsMedian()),
                            chosen ? span(GAP + build.title(), Theme.Classes.BOLD) : Span.raw(GAP + build.title())))
                    .toJavaList());
        }

        /**
         * What {@code build} ran for, marked when it ran for the branch, when the builds are the runs of their
         * workflow on every branch; nothing else.
         */
        private List<Span> forWhat(Build build, ListColumns columns) {
            if (!allBranches) {
                return List.empty();
            }
            boolean mine = isMine(build);
            return List.of(
                    mine ? span(MINE, Theme.Classes.CURRENT_BRANCH) : Span.raw(" "),
                    Span.raw(" "),
                    span(
                            pad(ranFor(build), columns.ranFor()),
                            mine ? Theme.Classes.CURRENT_BRANCH : Theme.Classes.DIM));
        }

        /**
         * How much longer a build took than the median, {@code difference}, {@code width} columns wide.
         */
        private Span vsMedian(Option<Duration> difference, int width) {
            return difference
                    .map(change -> span(padLeft(signed(change), width), trend(change, Duration.ofSeconds(30))))
                    .getOrElse(() -> Span.raw(" ".repeat(width)));
        }
    }
}
