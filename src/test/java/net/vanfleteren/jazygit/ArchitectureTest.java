package net.vanfleteren.jazygit;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleName;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameStartingWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Program;

/**
 * Rules about which parts of the application may depend on which, and how.
 */
@AnalyzeClasses(packages = "net.vanfleteren.jazygit", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String ROOT = "net.vanfleteren.jazygit";
    // The dialogs of the features, which are views; nested classes included.
    private static final String DIALOGS = ".*Dialogs?(\\$.*)?";

    /**
     * The data read from git and GitHub is plain values, with nothing that runs anything.
     */
    @ArchTest
    static final ArchRule modelsArePure = classes()
            .that()
            .resideInAnyPackage("..jazygit.git.model..", "..jazygit.gh.model..")
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage("java..", "io.vavr..", "..jazygit.git.model..", "..jazygit.gh.model..");

    /**
     * The git, gh and process layers return data; what the application does with it, and turning it into text
     * for the user, is up to the layers above.
     */
    @ArchTest
    static final ArchRule lowLayersDoNotKnowTheApplication = noClasses()
            .that()
            .resideInAnyPackage("..jazygit.git..", "..jazygit.gh..", "..jazygit.process..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "..jazygit.state..",
                    "..jazygit.feature..",
                    "..jazygit.keys..",
                    "..jazygit.ui..",
                    "..jazygit.i18n..",
                    "dev.tamboui..");

    /**
     * git and gh are reached each on their own; the features combine them. The process layer runs any program,
     * and knows neither.
     */
    @ArchTest
    static final ArchRule gitKnowsNothingOfGh = noClasses()
            .that()
            .resideInAPackage("..jazygit.git..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..jazygit.gh..");

    @ArchTest
    static final ArchRule ghKnowsNothingOfGit = noClasses()
            .that()
            .resideInAPackage("..jazygit.gh..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..jazygit.git..");

    @ArchTest
    static final ArchRule processKnowsNeitherGitNorGh = noClasses()
            .that()
            .resideInAPackage("..jazygit.process..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..jazygit.git..", "..jazygit.gh..");

    /**
     * The settings stand on their own: what is kept, and where, knows nothing of the application.
     */
    @ArchTest
    static final ArchRule settingsKnowNothingOfTheApplication = classes()
            .that()
            .resideInAPackage("..jazygit.settings..")
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage(
                    "java..", "io.vavr..", "lombok..", "com.electronwill.nightconfig..", "..jazygit.settings..");

    /**
     * How the settings file is read and written stays inside the settings.
     */
    @ArchTest
    static final ArchRule onlyTheSettingsReadToml = noClasses()
            .that()
            .resideOutsideOfPackage("..jazygit.settings..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.electronwill.nightconfig..");

    /**
     * The command line stands on its own: it reads how jazygit is asked to start, and knows nothing of what is
     * started.
     */
    @ArchTest
    static final ArchRule cliKnowsNothingOfTheApplication = classes()
            .that()
            .resideInAPackage("..jazygit.cli..")
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage("java..", "io.vavr..", "picocli..", "..jazygit.cli..", "..jazygit.settings..");

    /**
     * How the command line is read stays inside the cli package.
     */
    @ArchTest
    static final ArchRule onlyTheCliUsesPicocli = noClasses()
            .that()
            .resideOutsideOfPackage("..jazygit.cli..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("picocli..");

    /**
     * {@code Exec} is the only place that starts a program.
     */
    @ArchTest
    static final ArchRule onlyExecStartsPrograms = noClasses()
            .that()
            .resideOutsideOfPackage("..jazygit.process..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("org.zeroturnaround.exec..")
            .orShould()
            .dependOnClassesThat()
            .belongToAnyOf(ProcessBuilder.class)
            .orShould()
            .callMethod(Runtime.class, "exec", String.class);

    /**
     * Rendering never talks to git or gh: the views only draw the model, and send messages.
     */
    @ArchTest
    static final ArchRule viewsDoNotRunGitOrGh = noClasses()
            .that()
            .resideInAnyPackage("..jazygit.ui..", "..jazygit.keys..")
            .or()
            .haveNameMatching(DIALOGS)
            .should()
            .dependOnClassesThat(runsGitOrGh());

    private static DescribedPredicate<JavaClass> runsGitOrGh() {
        return resideInAnyPackage("..jazygit.git", "..jazygit.gh")
                .and(simpleNameStartingWith("Cli").or(simpleNameEndingWith("Process")))
                .or(resideInAnyPackage("..jazygit.process").and(simpleName("Exec")))
                .as("run git or gh");
    }

    /**
     * The top-level parts depend on each other one way only. The keymap is the exception: it lists the keys
     * of the features, which declare them with its types.
     */
    @ArchTest
    static final ArchRule partsAreFreeOfCycles = slices().matching(ROOT + ".(*)..")
            .should()
            .beFreeOfCycles()
            .ignoreDependency(resideInAnyPackage("..jazygit.keys"), resideInAnyPackage("..jazygit.feature.."));

    /**
     * The core of the application knows neither the features built on it nor how it is shown.
     */
    @ArchTest
    static final ArchRule stateDoesNotKnowFeaturesOrViews = noClasses()
            .that()
            .resideInAPackage("..jazygit.state..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "..jazygit.feature..", "..jazygit.ui..", "..jazygit.keys..", "..jazygit.i18n..", "dev.tamboui..");

    /**
     * The updates, the messages they handle, the popups and the model are pure: running git or gh is left to
     * the commands, showing anything to the views. The commands that ran are only passed on as data.
     */
    @ArchTest
    static final ArchRule updatesArePure = noClasses()
            .that()
            .haveNameMatching(".*\\.(\\w*(Update|Msg|Popup)|Model|Preview|Loadable|Fetching|AppError|Operation"
                    + "|LogEntry|CommandLog)(\\$.*)?")
            .should()
            .dependOnClassesThat(effects())
            .orShould()
            .callMethod(java.time.Instant.class, "now")
            .orShould()
            .callMethod(System.class, "currentTimeMillis");

    private static DescribedPredicate<JavaClass> effects() {
        return resideInAnyPackage(
                        "..jazygit.git",
                        "..jazygit.gh",
                        "..jazygit.process..",
                        "..jazygit.ui..",
                        "..jazygit.i18n..",
                        "dev.tamboui..",
                        "java.io..",
                        "java.nio.file..")
                .or(JavaClass.Predicates.belongToAnyOf(Thread.class, ProcessBuilder.class, java.time.Clock.class))
                .and(not(equivalentTo(ExecutedCommand.class)))
                .as("run git or gh, or show something");
    }

    /**
     * Translated text and the terminal UI belong to the views: the ui and keys packages, the dialogs of the
     * features and the application itself.
     */
    @ArchTest
    static final ArchRule onlyViewsUseI18nOrTamboUi = noClasses()
            .that()
            .resideOutsideOfPackages("..jazygit.ui..", "..jazygit.keys..", "..jazygit.i18n..")
            .and()
            .resideOutsideOfPackage(ROOT)
            .and()
            .haveNameNotMatching(DIALOGS)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..jazygit.i18n..", "dev.tamboui..");

    /**
     * A feature may use another, but not the other way round as well.
     */
    @ArchTest
    static final ArchRule featuresAreFreeOfCycles =
            slices().matching(ROOT + ".feature.(*)..").should().beFreeOfCycles();

    /**
     * State changes by making new values, never by changing fields. Only the program, which holds the current
     * model, and the dialogs, which are views, keep what changes.
     */
    @ArchTest
    static final ArchRule fieldsAreFinal = fields().that()
            .areDeclaredInClassesThat()
            .resideInAnyPackage(
                    "..jazygit.state..",
                    "..jazygit.git.model..",
                    "..jazygit.gh.model..",
                    "..jazygit.feature..",
                    "..jazygit.settings..",
                    "..jazygit.cli..")
            .and()
            .areNotDeclaredIn(Program.class)
            .and()
            .areDeclaredInClassesThat()
            .haveNameNotMatching(DIALOGS)
            .should()
            .beFinal();
}
