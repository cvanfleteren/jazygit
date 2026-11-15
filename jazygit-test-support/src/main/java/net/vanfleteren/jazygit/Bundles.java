package net.vanfleteren.jazygit;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
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
    private static final Pattern LITERAL = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");

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
        Set<String> keys = keys(root);
        return literals(Path.of("src", "main", "java"))
                .filter(text -> text.startsWith(root + ".") && text.length() > root.length() + 1)
                .filter(text -> !keys.contains(text) && keys.forAll(key -> !key.startsWith(text)))
                .distinct()
                .sorted();
    }

    private static ResourceBundle bundle(String root) {
        return ResourceBundle.getBundle(PACKAGE + root + ".messages", Locale.ROOT);
    }

    private static List<String> literals(Path sources) {
        return Try.of(() -> {
                    try (Stream<Path> files = Files.walk(sources)) {
                        return List.ofAll(files.filter(file -> file.toString().endsWith(".java"))
                                .toList());
                    }
                })
                .get()
                .flatMap(file -> List.ofAll(LITERAL.matcher(read(file))
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
