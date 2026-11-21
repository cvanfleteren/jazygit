package net.vanfleteren.jazygit.app;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.control.Try;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import net.vanfleteren.jazygit.app.keys.Features;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Each forge can be deleted (ADR 171, 173, 174): removing its modules, their lines in the poms and the line of its
 * feature in {@link Features}, leaves an app that builds. This test checks that nothing else names them, in place of
 * a build of a copy with them deleted:
 *
 * <ul>
 *   <li>no class but the forge's own and {@link Features} uses a class of the forge's, in the main code of every module
 *       and in the tests on the app's classpath, those of the app and the test-jars it uses; nor does another forge;
 *   <li>{@link Features} uses the forge only by reading its {@code FEATURE}, on a line of its own, and imports it on
 *       another;
 *   <li>the poms name the forge's modules only as a module of their parent, as a dependency of the app and in the
 *       dependency management of the parent pom, so that no other module can use them, in its main code or its tests;
 *   <li>no other file of a module, as a service file, names a class of the forge's.
 * </ul>
 *
 * <p>That the app works without them its own tests show: they run without the forges ({@code jazygit.without}, set in
 * the pom), as GitHub's tests of the whole app are in {@code jazygit-github-app-tests}.
 */
class ForgesDeletableTest {

    /**
     * A forge that can be deleted.
     *
     * @param name      how its classes are named, as {@code GitHub} in {@code GitHubFeature}
     * @param packages  the packages of its classes
     * @param modules   its modules, by their artifact id
     * @param locations where its modules are, from the root of the repository
     */
    record Forge(String name, List<String> packages, Set<String> modules, List<Path> locations) {

        String feature() {
            return "net.vanfleteren.jazygit." + packages.head() + "." + name + "Feature";
        }

        String[] archPackages() {
            return packages.map(each -> "..jazygit." + each + "..").toJavaArray(String[]::new);
        }

        String packagePattern() {
            return "net\\.vanfleteren\\.jazygit\\.("
                    + packages.map(each -> each.replace(".", "\\.")).mkString("|") + ")(\\..*)?";
        }

        @Override
        public String toString() {
            return name;
        }
    }

    static List<Forge> forges() {
        return List.of(
                new Forge(
                        "GitHub",
                        List.of("features.github", "core.gh"),
                        HashSet.of("features-github", "core-gh", "jazygit-github-app-tests"),
                        List.of(
                                Path.of("jazygit-features", "features-github"),
                                Path.of("jazygit-core", "core-gh"),
                                Path.of("jazygit-github-app-tests"))),
                new Forge(
                        "Forgejo",
                        List.of("features.forgejo", "core.forgejo"),
                        HashSet.of("features-forgejo", "core-forgejo"),
                        List.of(
                                Path.of("jazygit-features", "features-forgejo"),
                                Path.of("jazygit-core", "core-forgejo"))));
    }

    /**
     * The poms that list the forges' modules as theirs, and those that depend on them, from the root of the repository.
     */
    private static final Set<Path> LISTING =
            HashSet.of(Path.of("pom.xml"), Path.of("jazygit-features", "pom.xml"), Path.of("jazygit-core", "pom.xml"));

    private static final Set<Path> DEPENDING = HashSet.of(Path.of("pom.xml"), Path.of("jazygit-app", "pom.xml"));

    private static final JavaClasses CLASSES = new ClassFileImporter().importPackages("net.vanfleteren.jazygit");

    /**
     * The root of the repository: the app's module is right under it, and the tests run in that module.
     */
    private static final Path ROOT = Path.of("").toAbsolutePath().getParent();

    @ParameterizedTest
    @MethodSource("forges")
    void onlyTheForgeAndTheListOfTheFeaturesUseIt(Forge forge) {
        noClasses()
                .that()
                .resideOutsideOfPackages(forge.archPackages())
                .and()
                .doNotBelongToAnyOf(Features.class)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(forge.archPackages())
                .because(forge.name() + " can be deleted (ADR 171, 174)")
                .check(CLASSES);
    }

    @ParameterizedTest
    @MethodSource("forges")
    void theListOfTheFeaturesOnlyReadsTheForgesFeature(Forge forge) {
        classes()
                .that()
                .belongToAnyOf(Features.class)
                .should(useOnlyThroughItsFeature(forge))
                .check(CLASSES);
    }

    @ParameterizedTest
    @MethodSource("forges")
    void theListOfTheFeaturesNamesTheForgeOnTwoLinesOfItsOwn(Forge forge) throws IOException {
        Path source = ROOT.resolve(
                Path.of("jazygit-app", "src", "main", "java", "net", "vanfleteren", "jazygit", "app", "keys"));
        List<String> naming = List.ofAll(Files.readAllLines(source.resolve("Features.java"), StandardCharsets.UTF_8))
                .map(String::strip)
                .filter(line -> line.contains(forge.name()));
        assertEquals(List.of("import " + forge.feature() + ";", forge.name() + "Feature.FEATURE,"), naming);
    }

    @ParameterizedTest
    @MethodSource("forges")
    void thePomsNameTheForgeOnlyToListItsModulesAndForTheAppToDependOnThem(Forge forge) {
        List<Path> poms = poms(forge);
        assertTrue(poms.contains(ROOT.resolve("pom.xml")), "the poms are read from the root of the repository");
        List<String> wrong = poms.flatMap(pom -> namings(forge, pom)
                .filter(naming -> !allowed(ROOT.relativize(pom), naming))
                .map(naming -> ROOT.relativize(pom) + ": " + naming));
        assertEquals(List.empty(), wrong);
    }

    @ParameterizedTest
    @MethodSource("forges")
    void noOtherFileOfAModuleNamesAClassOfTheForges(Forge forge) {
        List<String> naming = files(forge)
                .filter(file -> !file.getFileName().toString().endsWith(".java"))
                .filter(file -> !file.getFileName().toString().equals("pom.xml"))
                .filter(file -> Try.of(() -> Files.readString(file, StandardCharsets.UTF_8))
                        .map(text -> forge.packages().exists(each -> text.contains("jazygit." + each)))
                        .getOrElse(false))
                .map(file -> ROOT.relativize(file).toString());
        assertEquals(List.empty(), naming);
    }

    private static ArchCondition<JavaClass> useOnlyThroughItsFeature(Forge forge) {
        return new ArchCondition<>("use " + forge.name() + " only by reading " + forge.feature() + ".FEATURE") {
            @Override
            public void check(JavaClass type, ConditionEvents events) {
                List<Dependency> forges = List.ofAll(type.getDirectDependenciesFromSelf())
                        .filter(dependency ->
                                dependency.getTargetClass().getPackageName().matches(forge.packagePattern()));
                forges.reject(dependency -> readsTheFeature(forge, dependency))
                        .forEach(dependency ->
                                events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription())));
                if (forges.isEmpty()) {
                    events.add(SimpleConditionEvent.violated(type, type.getName() + " does not list " + forge.name()));
                }
            }
        };
    }

    private static boolean readsTheFeature(Forge forge, Dependency dependency) {
        return dependency.getTargetClass().getName().equals(forge.feature())
                && dependency.getDescription().contains("gets field <" + forge.feature() + ".FEATURE>");
    }

    /**
     * Where a pom names one of a forge's modules: the element that names it, and that element's parent.
     */
    private record Naming(String module, String element, String parent, String grandparent) {

        @Override
        public String toString() {
            return grandparent + "/" + parent + "/" + element + " " + module;
        }
    }

    private static boolean allowed(Path pom, Naming naming) {
        boolean listed = naming.element().equals("module") && LISTING.contains(pom);
        boolean dependedOn = naming.element().equals("artifactId")
                && naming.parent().equals("dependency")
                && DEPENDING.contains(pom)
                && (!pom.equals(Path.of("pom.xml")) || naming.grandparent().equals("dependencies"));
        return listed || dependedOn;
    }

    /**
     * Where {@code pom} names one of the modules of {@code forge}.
     */
    private static List<Naming> namings(Forge forge, Path pom) {
        Element root = Try.of(() -> DocumentBuilderFactory.newInstance()
                        .newDocumentBuilder()
                        .parse(pom.toFile())
                        .getDocumentElement())
                .get();
        return elements(root)
                .filter(element ->
                        forge.modules().contains(element.getTextContent().strip())
                                && !element.getTagName().equals("project"))
                .filter(element -> element.getChildNodes().getLength() == 1)
                .map(element -> new Naming(
                        element.getTextContent().strip(),
                        element.getTagName(),
                        tagName(element.getParentNode()),
                        tagName(element.getParentNode().getParentNode())));
    }

    private static String tagName(Node node) {
        return node instanceof Element element ? element.getTagName() : "";
    }

    private static List<Element> elements(Element element) {
        NodeList children = element.getChildNodes();
        List<Element> nested = List.range(0, children.getLength())
                .map(children::item)
                .filter(Element.class::isInstance)
                .map(Element.class::cast);
        return List.of(element).appendAll(nested.flatMap(ForgesDeletableTest::elements));
    }

    /**
     * The poms of the modules but the forge's, which list them as theirs or depend on them.
     */
    private static List<Path> poms(Forge forge) {
        return walk(forge).filter(file -> file.getFileName().toString().equals("pom.xml"));
    }

    /**
     * The files of the modules but the forge's, under their sources.
     */
    private static List<Path> files(Forge forge) {
        return walk(forge).filter(file -> ROOT.relativize(file).toString().contains("src"));
    }

    /**
     * Every file of the repository but the forge's modules, what the build makes and what is hidden.
     */
    private static List<Path> walk(Forge forge) {
        Set<Path> skipped = HashSet.ofAll(forge.locations().map(ROOT::resolve));
        try (Stream<Path> files = Files.walk(ROOT)) {
            return List.ofAll(files.filter(Files::isRegularFile).filter(file -> {
                Path relative = ROOT.relativize(file);
                return skipped.forAll(module -> !file.startsWith(module))
                        && List.ofAll(relative).dropRight(1).forAll(part -> {
                            String name = part.toString();
                            return !name.startsWith(".") && !name.equals("target") && !name.equals("node_modules");
                        });
            }));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
