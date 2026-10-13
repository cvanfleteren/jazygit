package net.vanfleteren.jazygit;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Program;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

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
            .that().resideInAnyPackage("..jazygit.git.model..", "..jazygit.gh.model..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage("java..", "io.vavr..", "..jazygit.git.model..", "..jazygit.gh.model..");

    /**
     * The git, gh and process layers return data; what the application does with it, and turning it into text
     * for the user, is up to the layers above.
     */
    @ArchTest
    static final ArchRule lowLayersDoNotKnowTheApplication = noClasses()
            .that().resideInAnyPackage("..jazygit.git..", "..jazygit.gh..", "..jazygit.process..")
            .should().dependOnClassesThat().resideInAnyPackage("..jazygit.state..", "..jazygit.feature..",
                    "..jazygit.keys..", "..jazygit.ui..", "..jazygit.i18n..", "dev.tamboui..");

    /**
     * The core of the application knows neither the features built on it nor how it is shown.
     */
    @ArchTest
    static final ArchRule stateDoesNotKnowFeaturesOrViews = noClasses()
            .that().resideInAPackage("..jazygit.state..")
            .should().dependOnClassesThat().resideInAnyPackage("..jazygit.feature..", "..jazygit.ui..",
                    "..jazygit.keys..", "..jazygit.i18n..", "dev.tamboui..");

    /**
     * The updates, the messages they handle, the popups and the model are pure: running git or gh is left to
     * the commands, showing anything to the views. The commands that ran are only passed on as data.
     */
    @ArchTest
    static final ArchRule updatesArePure = noClasses()
            .that().haveNameMatching(".*\\.(\\w*(Update|Msg|Popup)|Model)(\\$.*)?")
            .should().dependOnClassesThat(effects());

    private static DescribedPredicate<JavaClass> effects() {
        return resideInAnyPackage("..jazygit.git", "..jazygit.gh", "..jazygit.process..", "..jazygit.ui..",
                "..jazygit.i18n..", "dev.tamboui..")
                .and(not(equivalentTo(ExecutedCommand.class)))
                .as("run git or gh, or show something");
    }

    /**
     * Translated text and the terminal UI belong to the views: the ui and keys packages, the dialogs of the
     * features and the application itself.
     */
    @ArchTest
    static final ArchRule onlyViewsUseI18nOrTamboUi = noClasses()
            .that().resideOutsideOfPackages("..jazygit.ui..", "..jazygit.keys..", "..jazygit.i18n..")
            .and().resideOutsideOfPackage(ROOT)
            .and().haveNameNotMatching(DIALOGS)
            .should().dependOnClassesThat().resideInAnyPackage("..jazygit.i18n..", "dev.tamboui..");

    /**
     * A feature may use another, but not the other way round as well.
     */
    @ArchTest
    static final ArchRule featuresAreFreeOfCycles = slices()
            .matching(ROOT + ".feature.(*)..")
            .should().beFreeOfCycles();

    /**
     * State changes by making new values, never by changing fields. Only the program, which holds the current
     * model, and the dialogs, which are views, keep what changes.
     */
    @ArchTest
    static final ArchRule fieldsAreFinal = fields()
            .that().areDeclaredInClassesThat().resideInAnyPackage("..jazygit.state..", "..jazygit.git.model..",
                    "..jazygit.gh.model..", "..jazygit.feature..")
            .and().areNotDeclaredIn(Program.class)
            .and().areDeclaredInClassesThat().haveNameNotMatching(DIALOGS)
            .should().beFinal();
}
