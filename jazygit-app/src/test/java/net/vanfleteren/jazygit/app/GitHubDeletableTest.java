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
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * GitHub can be deleted (ADR 171, ADR 173): removing {@code features-github}, {@code core-gh} and
 * {@code jazygit-github-app-tests}, their lines in the poms and the line of {@code GitHubFeature.FEATURE} in
 * {@link Features}, leaves an app that builds. This test checks that nothing else names them, in place of a build of a
 * copy with them deleted:
 *
 * <ul>
 *   <li>no class but GitHub's own and {@link Features} uses a class of GitHub's, in the main code of every module and
 *       in the tests on the app's classpath, those of the app and the test-jars it uses;
 *   <li>{@link Features} uses GitHub only by reading {@code GitHubFeature.FEATURE}, on a line of its own, and imports
 *       it on another;
 *   <li>the poms name GitHub's modules only as a module of their parent, as a dependency of the app and in the
 *       dependency management of the parent pom, so that no other module can use them, in its main code or its tests;
 *   <li>no other file of a module, as a service file, names a class of GitHub's.
 * </ul>
 *
 * <p>That the app works without GitHub its own tests show: they run without it ({@code jazygit.without}, set in the
 * pom), as GitHub's tests of the whole app are in {@code jazygit-github-app-tests}.
 */
class GitHubDeletableTest {

    private static final String[] GITHUB_PACKAGES = {"..jazygit.features.github..", "..jazygit.core.gh.."};

    private static final String GITHUB_FEATURE = "net.vanfleteren.jazygit.features.github.GitHubFeature";

    /**
     * GitHub's modules, by their artifact id.
     */
    private static final Set<String> MODULES = HashSet.of("features-github", "core-gh", "jazygit-github-app-tests");

    /**
     * Where GitHub's modules are, from the root of the repository.
     */
    private static final List<Path> MODULE_DIRECTORIES = List.of(
            Path.of("jazygit-features", "features-github"),
            Path.of("jazygit-core", "core-gh"),
            Path.of("jazygit-github-app-tests"));

    /**
     * The poms that list GitHub's modules as theirs, and those that depend on them, from the root of the repository.
     */
    private static final Set<Path> LISTING =
            HashSet.of(Path.of("pom.xml"), Path.of("jazygit-features", "pom.xml"), Path.of("jazygit-core", "pom.xml"));

    private static final Set<Path> DEPENDING = HashSet.of(Path.of("pom.xml"), Path.of("jazygit-app", "pom.xml"));

    private static final JavaClasses CLASSES = new ClassFileImporter().importPackages("net.vanfleteren.jazygit");

    /**
     * The root of the repository: the app's module is right under it, and the tests run in that module.
     */
    private static final Path ROOT = Path.of("").toAbsolutePath().getParent();

    @Test
    void onlyGitHubAndTheListOfTheFeaturesUseGitHub() {
        noClasses()
                .that()
                .resideOutsideOfPackages(GITHUB_PACKAGES)
                .and()
                .doNotBelongToAnyOf(Features.class)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(GITHUB_PACKAGES)
                .because("GitHub can be deleted (ADR 171)")
                .check(CLASSES);
    }

    @Test
    void theListOfTheFeaturesOnlyReadsGitHubsFeature() {
        classes()
                .that()
                .belongToAnyOf(Features.class)
                .should(useGitHubOnlyThroughItsFeature())
                .check(CLASSES);
    }

    @Test
    void theListOfTheFeaturesNamesGitHubOnTwoLinesOfItsOwn() throws IOException {
        Path source = ROOT.resolve(
                Path.of("jazygit-app", "src", "main", "java", "net", "vanfleteren", "jazygit", "app", "keys"));
        List<String> naming = List.ofAll(Files.readAllLines(source.resolve("Features.java"), StandardCharsets.UTF_8))
                .map(String::strip)
                .filter(line -> line.contains("GitHub"));
        assertEquals(List.of("import " + GITHUB_FEATURE + ";", "GitHubFeature.FEATURE,"), naming);
    }

    @Test
    void thePomsNameGitHubOnlyToListItsModulesAndForTheAppToDependOnThem() {
        List<Path> poms = poms();
        assertTrue(poms.contains(ROOT.resolve("pom.xml")), "the poms are read from the root of the repository");
        List<String> wrong = poms.flatMap(pom -> namings(pom)
                .filter(naming -> !allowed(ROOT.relativize(pom), naming))
                .map(naming -> ROOT.relativize(pom) + ": " + naming));
        assertEquals(List.empty(), wrong);
    }

    @Test
    void noOtherFileOfAModuleNamesAClassOfGitHubs() {
        List<String> naming = files().filter(
                        file -> !file.getFileName().toString().endsWith(".java"))
                .filter(file -> !file.getFileName().toString().equals("pom.xml"))
                .filter(file -> Try.of(() -> Files.readString(file, StandardCharsets.UTF_8))
                        .map(text -> text.contains("jazygit.features.github") || text.contains("jazygit.core.gh"))
                        .getOrElse(false))
                .map(file -> ROOT.relativize(file).toString());
        assertEquals(List.empty(), naming);
    }

    private static ArchCondition<JavaClass> useGitHubOnlyThroughItsFeature() {
        return new ArchCondition<>("use GitHub only by reading " + GITHUB_FEATURE + ".FEATURE") {
            @Override
            public void check(JavaClass type, ConditionEvents events) {
                List<Dependency> gitHubs = List.ofAll(type.getDirectDependenciesFromSelf())
                        .filter(dependency -> dependency
                                .getTargetClass()
                                .getPackageName()
                                .matches("net\\.vanfleteren\\.jazygit\\.(features\\.github|core\\.gh)(\\..*)?"));
                gitHubs.reject(GitHubDeletableTest::readsTheFeature)
                        .forEach(dependency ->
                                events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription())));
                if (gitHubs.isEmpty()) {
                    events.add(SimpleConditionEvent.violated(type, type.getName() + " does not list GitHub"));
                }
            }
        };
    }

    private static boolean readsTheFeature(Dependency dependency) {
        return dependency.getTargetClass().getName().equals(GITHUB_FEATURE)
                && dependency.getDescription().contains("gets field <" + GITHUB_FEATURE + ".FEATURE>");
    }

    /**
     * Where a pom names one of GitHub's modules: the element that names it, and that element's parent.
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
     * Where {@code pom} names one of GitHub's modules.
     */
    private static List<Naming> namings(Path pom) {
        Element root = Try.of(() -> DocumentBuilderFactory.newInstance()
                        .newDocumentBuilder()
                        .parse(pom.toFile())
                        .getDocumentElement())
                .get();
        return elements(root)
                .filter(element -> MODULES.contains(element.getTextContent().strip())
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
        return List.of(element).appendAll(nested.flatMap(GitHubDeletableTest::elements));
    }

    /**
     * The poms of the modules but GitHub's, which list them as theirs or depend on them.
     */
    private static List<Path> poms() {
        return walk().filter(file -> file.getFileName().toString().equals("pom.xml"));
    }

    /**
     * The files of the modules but GitHub's, under their sources.
     */
    private static List<Path> files() {
        return walk().filter(file -> ROOT.relativize(file).toString().contains("src"));
    }

    /**
     * Every file of the repository but GitHub's modules, what the build makes and what is hidden.
     */
    private static List<Path> walk() {
        Set<Path> skipped = HashSet.ofAll(MODULE_DIRECTORIES.map(ROOT::resolve));
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
