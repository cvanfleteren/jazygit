package net.vanfleteren.jazygit.state.view;

import io.vavr.Tuple;
import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import java.util.function.Function;
import java.util.regex.Pattern;
import net.vanfleteren.jazygit.yaml.YamlTree;

/**
 * The jobs of a workflow of GitHub Actions, as its file says them, and which job needs which: what the check runs of
 * a build do not say (ADR 131).
 *
 * @param jobs its jobs, in the order they are written
 */
public record Workflow(List<Workflow.Job> jobs) {

    // A value of an expression in a name, like the shard of a matrix: "acceptance (${{ matrix.shard }}/6)".
    private static final Pattern EXPRESSION = Pattern.compile("\\$\\{\\{.*?}}");
    private static final String LOCAL = "./";

    /**
     * The workflow {@code text} says; none when it is not YAML, or has no jobs.
     */
    public static Option<Workflow> parse(String text) {
        return YamlTree.read(text)
                .toOption()
                .flatMap(documents -> documents.headOption())
                .flatMap(document -> field(document.root(), "jobs"))
                .flatMap(jobs -> jobs instanceof YamlTree.Mapping(var line, var end, var properties, var fields)
                        ? Option.of(new Workflow(fields.map(job -> new Job(
                                job.key(),
                                field(job.value(), "name").flatMap(Workflow::scalar),
                                field(job.value(), "needs")
                                        .map(Workflow::scalars)
                                        .getOrElse(List.empty()),
                                field(job.value(), "uses").flatMap(Workflow::scalar)))))
                        : Option.none());
    }

    /**
     * The paths of the workflows of this repository its jobs call, as they are named in it: those of other
     * repositories are not here to be read.
     */
    public List<String> called() {
        return jobs.flatMap(Job::called).distinct();
    }

    /**
     * Which of {@code names}, the jobs of a build of this workflow as GitHub names them, each one needs: those it
     * waits for directly, not those it waits for through another. A job whose name does not match one of this
     * workflow is left out, as is one that needs none. {@code called} gives the workflows its jobs call, by path;
     * the jobs of one it does not give are left out too.
     */
    public Map<String, Set<String>> needs(List<String> names, Function<String, Option<Workflow>> called) {
        List<Node> nodes = nodes(called);
        Map<String, Node> matched = HashMap.ofEntries(
                names.flatMap(name -> nodes.find(node -> node.matches(name)).map(node -> Tuple.of(name, node))));
        Map<String, Set<String>> direct = matched.mapValues(node -> HashSet.ofAll(names.filter(
                other -> node.needs().exists(needed -> needed.matcher(other).matches()))));
        return direct.mapValues(needed -> needed.filter(
                        one -> !needed.exists(other -> !other.equals(one) && waitsFor(direct, other, one, 0))))
                .filterValues(needed -> !needed.isEmpty());
    }

    /**
     * Whether {@code job} waits for {@code other}, directly or through others.
     */
    private static boolean waitsFor(Map<String, Set<String>> direct, String job, String other, int depth) {
        Set<String> needed = direct.get(job).getOrElse(HashSet.empty());
        return depth < 64
                && (needed.contains(other) || needed.exists(next -> waitsFor(direct, next, other, depth + 1)));
    }

    /**
     * The jobs of this workflow as patterns of the names GitHub gives them, each with what it needs: a job that calls
     * a workflow is the jobs of that one, named after it, and those of them that need none need what it needs.
     */
    private List<Node> nodes(Function<String, Option<Workflow>> called) {
        return jobs.flatMap(job -> job.called()
                .flatMap(called)
                .map(workflow -> workflow.jobs.map(inner -> new Node(
                        prefixed(job.shown(), inner.pattern()),
                        inner.needs().isEmpty()
                                ? job.needs().flatMap(needed -> targets(needed, called))
                                : inner.needs()
                                        .flatMap(needed -> workflow.job(needed))
                                        .map(other -> prefixed(job.shown(), other.pattern())))))
                .getOrElse(() ->
                        List.of(new Node(job.pattern(), job.needs().flatMap(needed -> targets(needed, called))))));
    }

    /**
     * The patterns of the jobs that end the job {@code key}: itself, or, when it calls a workflow, the jobs of that one
     * no other job of it needs.
     */
    private List<Pattern> targets(String key, Function<String, Option<Workflow>> called) {
        return job(key).toList()
                .flatMap(job -> job.called()
                        .flatMap(called)
                        .map(workflow -> workflow.jobs
                                .filter(inner -> !workflow.jobs.exists(
                                        other -> other.needs().contains(inner.key())))
                                .map(inner -> prefixed(job.shown(), inner.pattern())))
                        .getOrElse(() -> List.of(job.pattern())));
    }

    private Option<Job> job(String key) {
        return jobs.find(job -> job.key().equals(key));
    }

    private static Pattern prefixed(String caller, Pattern inner) {
        return Pattern.compile(Pattern.quote(caller + " / ") + inner.pattern());
    }

    private static Option<YamlTree> field(YamlTree tree, String key) {
        return tree instanceof YamlTree.Mapping(var line, var end, var properties, var fields)
                ? fields.find(field -> field.key().equals(key)).map(YamlTree.Field::value)
                : Option.none();
    }

    private static Option<String> scalar(YamlTree tree) {
        return tree instanceof YamlTree.Scalar(var line, var end, var properties, var kind, var style, String text)
                ? Option.of(unquoted(text))
                : Option.none();
    }

    private static List<String> scalars(YamlTree tree) {
        return tree instanceof YamlTree.Sequence(var line, var end, var properties, List<YamlTree> elements)
                ? elements.flatMap(Workflow::scalar)
                : scalar(tree).toList();
    }

    private static String unquoted(String text) {
        return text.length() >= 2
                        && (text.startsWith("'") && text.endsWith("'") || text.startsWith("\"") && text.endsWith("\""))
                ? text.substring(1, text.length() - 1)
                : text;
    }

    /**
     * A job of a workflow.
     *
     * @param key   its key under {@code jobs}
     * @param name  its {@code name}, which may hold expressions; none when it has none, and is named by its key
     * @param needs the keys of the jobs it needs
     * @param uses  the workflow it calls, or the action it is; none for a job of steps
     */
    public record Job(String key, Option<String> name, List<String> needs, Option<String> uses) {

        /**
         * The name GitHub shows for it, its expressions as they are written.
         */
        String shown() {
            return name.getOrElse(key);
        }

        /**
         * The names GitHub gives its runs: its name, with anything for an expression in it; or its key, followed by
         * the values of its matrix, if it has one.
         */
        Pattern pattern() {
            return name.map(written -> Pattern.compile(List.of(EXPRESSION.split(written, -1))
                            .map(Pattern::quote)
                            .mkString(".+?")))
                    .getOrElse(() -> Pattern.compile(Pattern.quote(key) + "( \\(.*\\))?"));
        }

        /**
         * The path of the workflow of this repository it calls, like {@code .github/workflows/build.yml}.
         */
        Option<String> called() {
            return uses.filter(path -> path.startsWith(LOCAL) && !path.contains("@"))
                    .map(path -> path.substring(LOCAL.length()));
        }
    }

    /**
     * A job as the names of its runs match it, and the patterns of the names of those it needs.
     */
    private record Node(Pattern pattern, List<Pattern> needs) {

        boolean matches(String name) {
            return pattern.matcher(name).matches();
        }
    }
}
