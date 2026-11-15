package net.vanfleteren.jazygit.ui.content;

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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.regex.Pattern;
import net.vanfleteren.jazygit.gh.model.Build;
import net.vanfleteren.jazygit.gh.model.Build.Outcome;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.view.BuildsShown;
import net.vanfleteren.jazygit.state.view.BuildsView;
import net.vanfleteren.jazygit.state.view.BuildsView.Pipeline;
import net.vanfleteren.jazygit.ui.text.Placeholders;
import net.vanfleteren.jazygit.ui.text.TextWidth;
import net.vanfleteren.jazygit.ui.theme.Theme;

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
    // The widest a job's name is shown.
    private static final int NAME = 28;
    // A trend smaller than this is no change.
    private static final Duration STEADY = Duration.ofSeconds(10);
    private static final String EIGHTHS = " ▁▂▃▄▅▆▇█";
    private static final String SPARKS = "▁▂▃▄▅▆▇█";
    // What marks a run of the branch among those of every branch.
    private static final String MINE = "◆";
    // The widest the column of what a run on every branch ran for is: its pull request, else its branch.
    private static final int FOR = 14;
    // The widest a build's bar in the chart is, with the column after it, when there are few builds.
    private static final int WIDEST = 5;
    // A job split in shards, like "acceptance (1/6)", is one job.
    private static final Pattern SHARD = Pattern.compile("\\s*\\(\\d+/\\d+\\)$");

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
                DateTimeFormatter.ofPattern(Messages.get("content.builds.dayPattern"))
                        .withZone(zone),
                DateTimeFormatter.ofPattern(Messages.get("content.builds.whenPattern"))
                        .withZone(zone),
                Math.max(10, width - SCALE - 1));
        String workflow = shown.shown().workflow();
        List<Line> builds = switch (shown.builds()) {
            case Loadable.Loading<List<Build>>() ->
                List.of(Line.from(drawing.dim(Messages.get("content.builds.readingRuns", workflow))));
            case Loadable.Failed<List<Build>>(var failure) ->
                List.of(Line.from(Span.styled(Placeholders.error(failure), styles.of(Theme.Classes.ERROR))));
            case Loadable.Loaded<List<Build>>(var read) ->
                read.isEmpty()
                        ? List.of(Line.from(drawing.dim(Messages.get("content.builds.noRuns", workflow))))
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

    private static List<TabStrip.Tab> tabs(BuildsShown shown) {
        return shown.tabs()
                .zip(shown.lasts())
                .map(tab -> new TabStrip.Tab(tab._1().label(shown.tabs()), tab._2()));
    }

    /**
     * {@code took} as the builds show it: {@code 45s}, {@code 4m08}, {@code 1h02}.
     */
    static String duration(Duration took) {
        long seconds = Math.max(0, took.toSeconds());
        if (seconds < 60) {
            return seconds + "s";
        }
        return seconds < 3600
                ? "%dm%02d".formatted(seconds / 60, seconds % 60)
                : "%dh%02d".formatted(seconds / 3600, seconds % 3600 / 60);
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

    private static String padLeft(String text, int width) {
        int shown = TextWidth.of(text);
        return shown >= width ? text : " ".repeat(width - shown) + text;
    }

    /**
     * The widths of the columns of the jobs, before their numbers.
     *
     * @param name   of the names of the jobs
     * @param passed of whether each passed
     * @param time   of how long each took
     */
    private record Columns(int name, int passed, int time) {}

    /**
     * How the builds are drawn.
     *
     * @param styles the styles of the theme
     * @param needs  which jobs each job needs, by the id of the run of a build whose jobs show on one time line
     * @param mine   the commits of the branch, whose runs are marked among those of every branch
     * @param allBranches whether the builds are the runs of their workflow on every branch
     * @param baseline how long the runs of the workflow that passed took on every branch
     * @param of     how many builds of the tab are read, of which some show
     * @param day    how a day is written
     * @param when   how a day and a time are written
     * @param chart  the builds the chart has room for
     */
    private record Drawing(
            Theme.SpanStyles styles,
            Map<Long, Map<String, Set<String>>> needs,
            Set<String> mine,
            boolean allBranches,
            Option<BuildsShown.Baseline> baseline,
            int of,
            DateTimeFormatter day,
            DateTimeFormatter when,
            int chart) {

        /**
         * The lines that draw the builds of one workflow, the newest first.
         */
        List<Line> workflow(List<Build> builds) {
            Option<Duration> median = median(passed(builds));
            return List.of(title(builds), summary(builds, median), Line.from(""))
                    .appendAll(chart(builds.take(chart).reverse(), median))
                    .append(Line.from(""))
                    .appendAll(jobs(builds.take(JOB_BUILDS).reverse()))
                    .appendAll(lastBuild(builds))
                    .append(Line.from(""))
                    .appendAll(list(builds, median));
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
            String event = allBranches ? Messages.get("content.builds.allEvents") : newest.event();
            String from = day.format(builds.last().createdAt());
            String to = day.format(newest.createdAt());
            return Line.from(
                    span(newest.workflow(), Theme.Classes.BOLD),
                    dim(
                            of > builds.size()
                                    ? Messages.get(
                                            "content.builds.workflowOf",
                                            event,
                                            String.valueOf(builds.size()),
                                            String.valueOf(of),
                                            from,
                                            to)
                                    : Messages.get(
                                            "content.builds.workflow",
                                            event,
                                            String.valueOf(builds.size()),
                                            from,
                                            to)));
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
                            "content.builds.times",
                            String.valueOf(Math.round(100.0 * passed.size() / builds.size())),
                            duration(middle),
                            percentile(passed, 0.9).map(BuildsText::duration).getOrElse("")))))
                    .getOrElse(List.empty())
                    .appendAll(last.flatMap(build -> build.duration()
                                    .map(took -> List.of(
                                            dim("   " + Messages.get("content.builds.last", duration(took))),
                                            median.map(middle -> took.minus(middle))
                                                    .map(difference -> span(
                                                            " " + signed(difference),
                                                            difference.compareTo(STEADY) > 0
                                                                    ? Theme.Classes.CHECKS_FAILED
                                                                    : difference.compareTo(STEADY.negated()) < 0
                                                                            ? Theme.Classes.CHECKS_PASSED
                                                                            : Theme.Classes.DIM))
                                                    .getOrElse(() -> dim("")))))
                            .getOrElse(List.empty()));
            List<Span> everyBranch = baseline.map(known -> List.of(dim(Messages.get(
                            "content.builds.baseline", duration(known.median()), String.valueOf(known.runs())))))
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
                return List.of(Line.from(dim(Messages.get("content.builds.noneDone"))));
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
            List<Boolean> starts = days.zipWithIndex()
                    .map(dated -> dated._2() == 0 || !dated._1().equals(days.get(dated._2() - 1)));
            String labels = days.zip(starts)
                    .zipWithIndex()
                    .foldLeft(
                            "",
                            (done, dated) -> dated._1()._2() && done.length() <= dated._2() * each
                                    ? done + " ".repeat(dated._2() * each - done.length())
                                            + dated._1()._1() + " "
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
                                    .append(dim(Messages.get("content.builds.mine")))
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
                    .map(number -> "#" + number)
                    .orElse(build.origin().branch())
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
            int width = Math.min(NAME, names.map(TextWidth::of).max().getOrElse(0)) + 1;
            int count = builds.size();
            String passedLabel = Messages.get("content.builds.lastBuilds", String.valueOf(count));
            String timeLabel = Messages.get("content.builds.time");
            // A column is as wide as its builds, or as its header with few of them, with two columns after it.
            Columns columns = new Columns(
                    width,
                    Math.max(count, TextWidth.of(passedLabel)) + 2,
                    Math.max(count, TextWidth.of(timeLabel)) + 2);
            Line header = Line.from(dim(pad(Messages.get("content.builds.job"), width)
                    + pad(passedLabel, columns.passed())
                    + pad(timeLabel, columns.time())
                    + Messages.get("content.builds.jobTimes")));
            return names.map(name -> job(name, builds.map(build -> ofJob(build, name)), columns))
                    .prepend(header)
                    .append(Line.from(""));
        }

        /**
         * How the job {@code name} did in {@code build}: how it ended and how long it took, of its slowest shard; none
         * when it did not run.
         */
        private Option<Tuple2<Outcome, Duration>> ofJob(Build build, String name) {
            List<Build.Job> shards =
                    build.ran().filter(job -> jobName(job.name()).equals(name));
            return Option.when(
                    !shards.isEmpty(),
                    () -> Tuple.of(
                            new Build(
                                            build.workflow(),
                                            build.event(),
                                            build.runId(),
                                            build.attempt(),
                                            build.sha(),
                                            build.title(),
                                            build.createdAt(),
                                            shards)
                                    .outcome(),
                            shards.flatMap(Build.Job::duration).max().getOrElse(Duration.ZERO)));
        }

        private Line job(String name, List<Option<Tuple2<Outcome, Duration>>> runs, Columns columns) {
            List<Duration> passed = runs.flatMap(run -> run)
                    .filter(run -> run._1() == Outcome.PASSED)
                    .map(Tuple2::_2);
            Duration low = passed.min().getOrElse(Duration.ZERO);
            Duration high = passed.max().getOrElse(Duration.ZERO);
            List<Span> results = runs.map(run -> run.map(done -> switch (done._1()) {
                        case PASSED -> span("▆", Theme.Classes.CHECKS_PASSED);
                        case FAILED -> span("▆", Theme.Classes.CHECKS_FAILED);
                        case CANCELLED, RUNNING -> dim("▁");
                    })
                    .getOrElse(() -> Span.raw(" ")));
            List<Span> sparks = runs.map(run -> run.map(done -> done._1() == Outcome.PASSED
                            ? Span.raw(String.valueOf(SPARKS.charAt(
                                    high.equals(low)
                                            ? 0
                                            : (int) Math.round(7.0
                                                    * done._2().minus(low).toMillis()
                                                    / high.minus(low).toMillis()))))
                            : done._1() == Outcome.FAILED ? span("▁", Theme.Classes.CHECKS_FAILED) : Span.raw(" "))
                    .getOrElse(() -> Span.raw(" ")));
            int half = passed.size() / 2;
            Option<Duration> trend = Option.when(
                    half > 0,
                    () -> median(passed.drop(half))
                            .get()
                            .minus(median(passed.take(half)).get()));
            Option<Duration> last =
                    runs.reverse().flatMap(run -> run).headOption().map(Tuple2::_2);
            return Line.from(List.of(Span.raw(pad(name, columns.name())))
                    .appendAll(results)
                    .append(Span.raw(" ".repeat(Math.max(0, columns.passed() - runs.size()))))
                    .appendAll(sparks)
                    .append(Span.raw(" ".repeat(Math.max(0, columns.time() - runs.size()))))
                    .append(Span.raw(
                            padLeft(median(passed).map(BuildsText::duration).getOrElse("–"), 6)))
                    .append(Span.raw(padLeft(last.map(BuildsText::duration).getOrElse("–"), 7)))
                    .append(trend.map(change -> change.compareTo(STEADY) > 0
                                    ? span("  ▲ " + signed(change), Theme.Classes.CHECKS_FAILED)
                                    : change.compareTo(STEADY.negated()) < 0
                                            ? span("  ▼ " + signed(change), Theme.Classes.CHECKS_PASSED)
                                            : dim("    " + signed(change)))
                            .getOrElse(() -> Span.raw("")))
                    .toJavaList());
        }

        /**
         * The jobs of the last build that is done on one time line, each hanging off the jobs it waits for (ADR 131).
         */
        private List<Line> lastBuild(List<Build> builds) {
            return BuildsView.lastDoneOf(builds)
                    .map(build -> {
                        Line header = Line.from(
                                outcome(build.outcome()),
                                Span.raw(" "),
                                span(
                                        build.sha()
                                                .substring(
                                                        0,
                                                        Math.min(7, build.sha().length())),
                                        Theme.Classes.SHA),
                                Span.raw(" " + build.title()),
                                dim(Messages.get(
                                        "content.builds.lastBuild",
                                        build.duration()
                                                .map(BuildsText::duration)
                                                .getOrElse("–"))));
                        Map<String, Set<String>> ofBuild =
                                needs.get(build.runId()).getOrElse(HashMap.empty());
                        return JobTrain.lines(build, ofBuild, chart + SCALE + 1, styles)
                                .prepend(header);
                    })
                    .getOrElse(List.empty());
        }

        /**
         * The builds one by one, the newest first: how it ended, its commit, when, how long it took, against the
         * median, and the commit's title.
         */
        private List<Line> list(List<Build> builds, Option<Duration> median) {
            String forLabel = Messages.get("content.builds.for");
            // As wide as what the builds ran for and its header, with a column after it, up to FOR.
            int forWidth = Math.min(
                    FOR,
                    builds.map(build -> TextWidth.of(ranFor(build)))
                                    .append(TextWidth.of(forLabel))
                                    .max()
                                    .getOrElse(0)
                            + 1);
            return builds.map(build -> {
                        Option<Duration> took = build.duration();
                        Option<Duration> difference = took.filter(done -> build.outcome() == Outcome.PASSED)
                                .flatMap(done -> median.map(done::minus));
                        List<Span> forWhat = allBranches
                                ? List.of(
                                        isMine(build) ? span(MINE, Theme.Classes.CURRENT_BRANCH) : Span.raw(" "),
                                        Span.raw(" "),
                                        span(
                                                pad(ranFor(build), forWidth),
                                                isMine(build) ? Theme.Classes.CURRENT_BRANCH : Theme.Classes.DIM))
                                : List.empty();
                        Span vsMedian = difference
                                .map(change -> span(
                                        padLeft(signed(change), 8),
                                        change.compareTo(Duration.ofSeconds(30)) > 0
                                                ? Theme.Classes.CHECKS_FAILED
                                                : change.compareTo(Duration.ofSeconds(-30)) < 0
                                                        ? Theme.Classes.CHECKS_PASSED
                                                        : Theme.Classes.DIM))
                                .getOrElse(() -> Span.raw(" ".repeat(8)));
                        return Line.from(List.of(outcome(build.outcome()), Span.raw(" "))
                                .appendAll(forWhat)
                                .appendAll(List.of(
                                        span(
                                                build.sha()
                                                        .substring(
                                                                0,
                                                                Math.min(
                                                                        7,
                                                                        build.sha()
                                                                                .length())),
                                                Theme.Classes.SHA),
                                        dim("  " + when.format(build.createdAt()) + "  "),
                                        Span.raw(padLeft(
                                                took.map(BuildsText::duration).getOrElse("–"), 5)),
                                        vsMedian,
                                        Span.raw("  " + build.title())))
                                .toJavaList());
                    })
                    .prepend(Line.from(dim("  " + (allBranches ? "  " + pad(forLabel, forWidth) : "")
                            + Messages.get("content.builds.list"))));
        }
    }
}
