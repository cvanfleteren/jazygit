package net.vanfleteren.jazygit;

import io.vavr.Tuple;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Try;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * What the tests of each module check of its bundle of text (ADR 136): that it holds the keys its code names, and
 * that they start with its root.
 */
public final class Bundles {

    private static final String PACKAGE = "net.vanfleteren.jazygit.";
    // The groups whose modules have text, then the app, whose package is in no group (ADR 159), as Messages has them.
    private static final List<String> GROUPS = List.of("kernel.", "features.", "");
    // Possessive, so a long text does not overflow the stack with a step of backtracking for every character.
    // A text shaped as a key, or as the start of one that the code finishes at run time.
    private static final Pattern KEY = Pattern.compile("[a-z][A-Za-z0-9]*+(\\.[A-Za-z0-9]++)++\\.?+");
    private static final Pattern LITERAL = Pattern.compile("\"((?:[^\"\\\\]++|\\\\.)*+)\"");
    // The name a part of the model, a service of the environment, a key of a pane or a tab of one is kept under, which
    // is shaped as a key but names no text (ADR 157, 169, 172, 185).
    private static final Pattern PART_NAME =
            Pattern.compile("((?:Part|Service|Slot)\\.of|new TabId)\\(\\s*\"(?:[^\"\\\\]++|\\\\.)*+\"");

    private Bundles() {}

    /**
     * The keys of the bundle of the module whose root is {@code root}, in English.
     */
    public static Set<String> keys(String root) {
        return HashSet.ofAll(bundle(root).keySet());
    }

    /**
     * The keys of the bundle of {@code root} whose text is blank.
     */
    public static List<String> blank(String root) {
        ResourceBundle bundle = bundle(root);
        return List.ofAll(bundle.keySet())
                .filter(key -> bundle.getString(key).isBlank())
                .sorted();
    }

    /**
     * The keys of the bundle of {@code root} that do not start with it.
     */
    public static List<String> unprefixed(String root) {
        return keys(root).filter(key -> !key.startsWith(root + ".")).toList().sorted();
    }

    /**
     * The texts in the main sources of this module ({@code src/main/java}) that start with {@code root} and a dot, but
     * are not a key of its bundle, nor the start of one: a key made at run time from such a text and the rest of it, as
     * {@code "github.error.review." + why}.
     */
    public static List<String> missing(String root) {
        return missing(root, HashSet.empty());
    }

    /**
     * As {@link #missing(String)}, but a text of {@code notKeys} is not asked for: one that starts with the root and
     * names something else, as the path of a setting in the settings file.
     */
    public static List<String> missing(String root, Set<String> notKeys) {
        Set<String> keys = keys(root);
        return literals(Path.of("src", "main", "java"))
                .filter(text -> text.startsWith(root + ".") && text.length() > root.length() + 1)
                .filter(text -> !notKeys.contains(text))
                .filter(text -> !keys.contains(text) && keys.forAll(key -> !key.startsWith(text)))
                .distinct()
                .sorted();
    }

    /**
     * The texts in the main sources of the modules under {@code project} with a bundle of {@code roots} that name a
     * key, but no key the views would find: a text whose first part is that of a key of one of those bundles, which is
     * neither a key of the bundle it is looked up in, nor the start of one, and is not one of {@code notKeys}. As
     * {@code Messages} does, a text is looked up in the bundle its first part names, and otherwise in that of the views,
     * {@code ui}. This also checks the keys of the views, which start with no root, and the keys of a module named
     * from another.
     *
     * @param notKeys texts shaped as a key that this cannot check: a text that names something else, as a host, or
     *                the start of a key that the code finishes inside its last part
     */
    public static List<String> unknown(Path project, List<String> roots, Set<String> notKeys) {
        Map<String, Set<String>> bundles = roots.toMap(root -> root, Bundles::keys);
        Set<String> views = bundles.get("ui").getOrElseThrow(() -> new IllegalArgumentException("no ui in " + roots));
        Set<String> firsts =
                bundles.values().flatMap(keys -> keys).map(Bundles::first).toSet();
        return mainSources(project, roots)
                .flatMap(Bundles::literals)
                .filter(text -> KEY.matcher(text).matches() && firsts.contains(first(text)))
                .filter(text -> !notKeys.contains(text))
                .filter(text ->
                        !known(bundles.get(first(text)).getOrElse(HashSet.empty()), text) && !known(views, text))
                .distinct()
                .sorted();
    }

    /**
     * The bundles of the modules under {@code project} that the native image would leave out: a module's
     * {@code messages.properties} whose name its {@code reachability-metadata.json} does not list. The native image
     * keeps only the bundles listed, so every key of such a bundle would show as {@code !key!} there, though it is
     * found on the JVM.
     */
    public static List<String> unregistered(Path project) {
        Path resources = Path.of("src", "main", "resources");
        return Try.of(() -> {
                    try (Stream<Path> paths = Files.walk(project, 12)) {
                        return List.ofAll(paths.filter(
                                        path -> path.getFileName().toString().equals("messages.properties")
                                                && path.toString().contains(resources.toString()))
                                .toList());
                    }
                })
                .get()
                .filter(bundle -> !bundle.toString().contains("/target/"))
                .map(bundle -> {
                    String path = bundle.toString();
                    int at = path.indexOf(resources.toString());
                    Path root =
                            Path.of(path.substring(0, at + resources.toString().length()));
                    String name = root.relativize(bundle)
                            .toString()
                            .replace(".properties", "")
                            .replace('/', '.');
                    return Tuple.of(root, name);
                })
                .filter(bundle -> !registered(bundle._1(), bundle._2()))
                .map(bundle -> bundle._2())
                .sorted();
    }

    // Whether a reachability-metadata.json under the resources root names the bundle.
    private static boolean registered(Path resources, String name) {
        Path images = resources.resolve("META-INF").resolve("native-image");
        return Files.isDirectory(images)
                && Try.of(() -> {
                            try (Stream<Path> files = Files.walk(images)) {
                                return List.ofAll(files.filter(file ->
                                                file.getFileName().toString().equals("reachability-metadata.json"))
                                        .toList());
                            }
                        })
                        .get()
                        .exists(file -> read(file).contains("\"" + name + "\""));
    }

    // A key, or the start of one that the code finishes at run time: up to a dot, so a key cut short is not known.
    private static boolean known(Set<String> keys, String text) {
        String start = text.endsWith(".") ? text : text + ".";
        return keys.contains(text) || keys.exists(key -> key.startsWith(start));
    }

    private static String first(String key) {
        int dot = key.indexOf('.');
        return dot < 0 ? key : key.substring(0, dot);
    }

    // The main sources of each module whose root is one of roots: a module of a group is one level deeper.
    private static List<Path> mainSources(Path project, List<String> roots) {
        Path main = Path.of("src", "main", "java");
        return Try.of(() -> {
                    try (Stream<Path> paths = Files.walk(project, 5)) {
                        return List.ofAll(paths.filter(path -> path.endsWith(main)
                                        && roots.exists(root -> GROUPS.exists(group -> Files.isDirectory(
                                                path.resolve((PACKAGE + group).replace('.', '/') + root)))))
                                .toList());
                    }
                })
                .get();
    }

    /**
     * Whether a module of the root {@code root} is there, with its bundle: a feature's may be deleted, as GitHub's
     * is in a build without it (ADR 171).
     */
    public static boolean exists(String root) {
        return GROUPS.exists(
                group -> Try.of(() -> ResourceBundle.getBundle(PACKAGE + group + root + ".messages", Locale.ROOT))
                        .isSuccess());
    }

    // A name has its keys in one module: with a bundle in two groups, the check of either could read the other's.
    private static ResourceBundle bundle(String root) {
        List<String> groups = GROUPS.filter(
                group -> Try.of(() -> ResourceBundle.getBundle(PACKAGE + group + root + ".messages", Locale.ROOT))
                        .isSuccess());
        if (groups.size() > 1) {
            throw new IllegalStateException(root + " has a bundle in more than one group: " + groups.mkString(", "));
        }
        return groups.headOption()
                .map(group -> ResourceBundle.getBundle(PACKAGE + group + root + ".messages", Locale.ROOT))
                .getOrElseThrow(() -> new IllegalArgumentException("no bundle for " + root));
    }

    private static List<String> literals(Path sources) {
        return Try.of(() -> {
                    try (Stream<Path> files = Files.walk(sources)) {
                        return List.ofAll(files.filter(file -> file.toString().endsWith(".java"))
                                .toList());
                    }
                })
                .get()
                .flatMap(file ->
                        List.ofAll(LITERAL.matcher(PART_NAME.matcher(read(file)).replaceAll("$1("))
                                .results()
                                .map(found -> found.group(1))
                                .toList()));
    }

    private static String read(Path file) {
        return Try.of(() -> Files.readString(file))
                .recover(IOException.class, failure -> {
                    throw new IllegalStateException("cannot read " + file, failure);
                })
                .get();
    }
}
