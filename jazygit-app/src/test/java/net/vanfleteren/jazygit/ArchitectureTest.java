package net.vanfleteren.jazygit;

import static com.tngtech.archunit.base.DescribedPredicate.anyElementThat;
import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.*;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Popup;
import net.vanfleteren.jazygit.state.Program;

/**
 * Rules about which parts of the application may depend on which, and how. That jazygit-core knows nothing of
 * the application the compiler sees to; its own rules are in its {@code CoreArchitectureTest}.
 */
@AnalyzeClasses(
        packages = "net.vanfleteren.jazygit",
        importOptions = {ImportOption.DoNotIncludeTests.class, ArchitectureTest.LeaveOutCoreAndJson.class})
class ArchitectureTest {

    /**
     * jazygit-core and jazygit-json are checked by their own rules; their classes, and the core's test-jar, are left
     * out here.
     */
    static final class LeaveOutCoreAndJson implements ImportOption {
        @Override
        public boolean includes(Location location) {
            return !location.contains("/jazygit-core/") && !location.contains("/jazygit-json/");
        }
    }

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
     * JSON is read through jazygit-json's {@code Json} and {@code Decoder}; Parsson, which comes along with it, is
     * not used here (ADR 42).
     */
    @ArchTest
    static final ArchRule nothingUsesParsson =
            noClasses().should().dependOnClassesThat().resideInAnyPackage("jakarta.json..", "org.eclipse.parsson..");
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
    // The updates, the messages, the popups and the model, which are pure; nested classes included.
    private static final String PURE =
            ".*\\.(\\w*(Update|Msg|Popup)|Model|Preview|Loadable|AppError|Operation|LogEntry|CommandLog)(\\$.*)?";
    /**
     * The updates, the messages they handle, the popups and the model are pure: running git or gh is left to
     * the commands, showing anything to the views. The commands that ran are only passed on as data.
     */
    @ArchTest
    static final ArchRule updatesArePure = noClasses()
            .that()
            .haveNameMatching(PURE)
            .should()
            .dependOnClassesThat(effects())
            .orShould()
            .accessTargetWhere(tellsTheTimeOrChance());
    // The dialogs of the features, which are views; nested classes included.
    private static final String DIALOGS = ".*Dialogs?(\\$.*)?";
    /**
     * What happens, what is asked for and what a dialog is about are values: records, or enums.
     */
    @ArchTest
    static final ArchRule messagesCommandsAndPopupsAreValues = classes()
            .that()
            .areAssignableTo(Msg.class)
            .or()
            .areAssignableTo(Cmd.class)
            .or()
            .areAssignableTo(Popup.class)
            .and()
            .areNotInterfaces()
            .should()
            .beAssignableTo(Record.class)
            .orShould()
            .beAssignableTo(Enum.class);
    /**
     * The messages of a feature are a sealed set, so that its update switches over all of them, without a default.
     */
    @ArchTest
    static final ArchRule featureMessagesAreSealed = classes()
            .that()
            .areInterfaces()
            .and()
            .areAssignableTo(Msg.class)
            .and()
            .doNotBelongToAnyOf(Msg.class)
            .and()
            .haveSimpleNameEndingWith("Msg")
            .should(ArchCondition.from(DescribedPredicate.describe(
                    "be sealed", type -> type.reflect().isSealed())));
    /**
     * An update is only functions: a class that cannot be made, of static methods and constants.
     */
    @ArchTest
    static final ArchRule updatesAreFunctions = classes()
            .that()
            .haveSimpleNameEndingWith("Update")
            .and()
            .areNotInterfaces()
            .should()
            .haveModifier(JavaModifier.FINAL)
            .andShould()
            .haveOnlyPrivateConstructors();

    @ArchTest
    static final ArchRule updatesKeepNothing = fields().that()
            .areDeclaredInClassesThat()
            .haveSimpleNameEndingWith("Update")
            .should()
            .beStatic();

    @ArchTest
    static final ArchRule updatesAreStaticMethods = methods()
            .that()
            .areDeclaredInClassesThat()
            .haveSimpleNameEndingWith("Update")
            .and()
            .areDeclaredInClassesThat()
            .areNotInterfaces()
            .should()
            .beStatic();
    /**
     * The updates make commands; only the program runs them, and {@code StartApp} those of the start screen (ADR 28).
     * A command may run another, as part of itself. A method reference, {@code Cmd::run}, is an access too, so it
     * counts as a call does.
     */
    @ArchTest
    static final ArchRule onlyTheProgramRunsCommands = noClasses()
            .that()
            .haveNameNotMatching("(" + Pattern.quote(Program.class.getName()) + "|"
                    + Pattern.quote("net.vanfleteren.jazygit.StartApp") + ")(\\$.*)?")
            .and()
            .areNotAssignableTo(Cmd.class)
            .should()
            .accessTargetWhere(DescribedPredicate.<JavaAccess<?>>describe(
                    "Cmd.run or GitOperationCmd.perform",
                    access -> access.getTargetOwner().isAssignableTo(Cmd.class)
                            && (access.getName().equals("run")
                                    || access.getName().equals("perform"))));
    /**
     * The views and the keys only send messages: they neither make commands nor apply a message themselves. They may
     * read one, as the data it is. A reference, {@code Msg::apply} or a command's {@code ::new}, counts as a call
     * does.
     */
    @ArchTest
    static final ArchRule viewsOnlySendMessages = noClasses()
            .that()
            .resideInAnyPackage("..jazygit.ui..", "..jazygit.keys..")
            .or()
            .haveNameMatching(DIALOGS)
            .or()
            .haveSimpleNameEndingWith("Keys")
            .should()
            .accessTargetWhere(DescribedPredicate.<JavaAccess<?>>describe(
                    "a constructor of a command, or Msg.apply",
                    access -> access.getTargetOwner().isAssignableTo(Cmd.class)
                                    && access.getName().equals(JavaConstructor.CONSTRUCTOR_NAME)
                            || access.getTargetOwner().isAssignableTo(Msg.class)
                                    && access.getName().equals("apply")));
    /**
     * A pure method returns what it works out: one that returns nothing could only have done something.
     */
    @ArchTest
    static final ArchRule pureCodeReturnsValues = noMethods()
            .that()
            .areDeclaredInClassesThat()
            .haveNameMatching(PURE)
            .and()
            .doNotHaveModifier(JavaModifier.SYNTHETIC)
            .should()
            .haveRawReturnType(void.class);
    /**
     * Pure code fails with a value, never by throwing. The {@code MatchException} the compiler adds to a switch over a
     * sealed type is never thrown.
     */
    @ArchTest
    static final ArchRule pureCodeThrowsNothing = noClasses()
            .that()
            .haveNameMatching(PURE)
            .should()
            .callConstructorWhere(DescribedPredicate.describe(
                    "a constructor of an exception",
                    call -> call.getTargetOwner().isAssignableTo(Throwable.class)
                            && !call.getTargetOwner().isEquivalentTo(MatchException.class)));
    /**
     * The commands report how they failed as messages: none declares an exception.
     */
    @ArchTest
    static final ArchRule commandsDeclareNoExceptions = methods()
            .that()
            .areDeclaredInClassesThat()
            .areAssignableTo(Cmd.class)
            .should()
            .notDeclareThrowableOfType(Exception.class);
    /**
     * The model is built of values all the way down: what a field holds never changes either.
     */
    @ArchTest
    static final ArchRule valuesAreValuesAllTheWayDown = fields().that()
            .areDeclaredInClassesThat()
            .resideInAnyPackage("..jazygit.state..", "..jazygit.feature..", "..jazygit.settings..", "..jazygit.cli..")
            .and()
            .areNotDeclaredIn(Program.class)
            .and()
            .areDeclaredInClassesThat()
            .haveNameNotMatching(DIALOGS)
            .and()
            .doNotHaveModifier(JavaModifier.SYNTHETIC)
            .should(Values.holdAValue());
    /**
     * State is never kept in a static field.
     */
    @ArchTest
    static final ArchRule staticFieldsKeepNothing = fields().that()
            .areStatic()
            .and()
            .doNotHaveModifier(JavaModifier.SYNTHETIC)
            .should()
            .beFinal()
            .andShould()
            .notHaveRawType(Values.mutable());
    /**
     * The model and the updates make new collections, never fill one in.
     */
    @ArchTest
    static final ArchRule pureCodeFillsNoCollections = noClasses()
            .that()
            .resideInAnyPackage("..jazygit.state..", "..jazygit.feature..")
            .and()
            .doNotBelongToAnyOf(Program.class)
            .and()
            .haveNameNotMatching(DIALOGS)
            .should()
            .callConstructorWhere(DescribedPredicate.describe(
                    "a constructor of a collection that changes or a StringBuilder",
                    call -> Values.mutable().test(call.getTargetOwner())));
    /**
     * A missing value is Vavr's {@code Option}, but where a library's interface says otherwise: picocli fills the
     * command line in as {@code Optional}.
     */
    @ArchTest
    static final ArchRule noJavaUtilOptional = noMethods()
            .that()
            .doNotHaveModifier(JavaModifier.SYNTHETIC)
            .and(not(implementALibraryMethod()))
            .and()
            .areDeclaredInClassesThat()
            .resideOutsideOfPackage("..jazygit.cli..")
            .should()
            .haveRawReturnType(Optional.class)
            .orShould()
            .haveRawParameterTypes(anyElementThat(equivalentTo(Optional.class)));
    /**
     * The model and its features are made of records and interfaces, not of classes that extend each other.
     */
    @ArchTest
    static final ArchRule noClassesExtendOthers = classes()
            .that()
            .resideInAnyPackage("..jazygit.state..", "..jazygit.feature..")
            .and()
            .haveNameNotMatching(DIALOGS)
            .should(Values.extendNothing());

    private static final String ROOT = "net.vanfleteren.jazygit";
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
     * A feature may use another, but not the other way round as well.
     */
    @ArchTest
    static final ArchRule featuresAreFreeOfCycles =
            slices().matching(ROOT + ".feature.(*)..").should().beFreeOfCycles();
    /**
     * Only {@code OnGitHub} names a branch for gh, from the branch on its remote, so the name of a local branch is not
     * handed to gh for it (ADR 40). A constructor reference, {@code GhBranch::new}, is an access too, so it counts as
     * a call does.
     */
    @ArchTest
    static final ArchRule onlyOnGitHubNamesBranchesForGh = noClasses()
            .that()
            .doNotHaveFullyQualifiedName("net.vanfleteren.jazygit.feature.github.OnGitHub")
            .should()
            .accessTargetWhere(DescribedPredicate.describe(
                    "a constructor of GhBranch",
                    access -> access.getTargetOwner().isEquivalentTo(net.vanfleteren.jazygit.gh.model.GhBranch.class)
                            && access.getName().equals("<init>")));
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
     * State changes by making new values, never by changing fields. Only the program, which holds the current
     * model, and the dialogs, which are views, keep what changes.
     */
    @ArchTest
    static final ArchRule fieldsAreFinal = fields().that()
            .areDeclaredInClassesThat()
            .resideInAnyPackage("..jazygit.state..", "..jazygit.feature..", "..jazygit.settings..", "..jazygit.cli..")
            .and()
            .areNotDeclaredIn(Program.class)
            .and()
            .areDeclaredInClassesThat()
            .haveNameNotMatching(DIALOGS)
            .should()
            .beFinal();
    /**
     * Collections are Vavr's, which never change: no method takes or returns one of java.util's, but where a
     * library's interface says so.
     */
    @ArchTest
    static final ArchRule noJavaUtilCollections = noMethods()
            .that()
            .doNotHaveModifier(JavaModifier.SYNTHETIC)
            .and(not(implementALibraryMethod()))
            .should()
            .haveRawReturnType(javaUtilCollections())
            .orShould()
            .haveRawParameterTypes(anyElementThat(javaUtilCollections()));

    private static DescribedPredicate<JavaClass> javaUtilCollections() {
        return assignableTo(Collection.class).or(assignableTo(Map.class));
    }

    private static DescribedPredicate<JavaMethod> implementALibraryMethod() {
        return DescribedPredicate.describe("implement a method of a library", method -> {
            JavaClass owner = method.getOwner();
            String[] parameters = method.getRawParameterTypes().stream()
                    .map(JavaClass::getName)
                    .toArray(String[]::new);
            return Stream.concat(owner.getAllRawSuperclasses().stream(), owner.getAllRawInterfaces().stream())
                    .filter(type -> !type.getPackageName().startsWith("net.vanfleteren.jazygit"))
                    .anyMatch(type ->
                            type.tryGetMethod(method.getName(), parameters).isPresent());
        });
    }

    private static DescribedPredicate<JavaClass> runsGitOrGh() {
        return resideInAnyPackage("..jazygit.git", "..jazygit.gh")
                .and(simpleNameStartingWith("Cli").or(simpleNameEndingWith("Process")))
                .or(resideInAnyPackage("..jazygit.process").and(simpleName("Exec")))
                .as("run git or gh");
    }

    /**
     * Reading the clock, the environment or chance; a method reference, like {@code Instant::now}, included.
     */
    private static DescribedPredicate<JavaAccess<?>> tellsTheTimeOrChance() {
        return DescribedPredicate.describe("tell the time, read the environment or draw a random number", access -> {
            JavaClass owner = access.getTargetOwner();
            String name = access.getName();
            return owner.getPackageName().equals("java.time") && name.equals("now")
                    || owner.isEquivalentTo(System.class)
                            && Stream.of("currentTimeMillis", "nanoTime", "getenv", "getProperty")
                                    .anyMatch(name::equals)
                    || owner.isEquivalentTo(Math.class) && name.equals("random")
                    || owner.isEquivalentTo(java.util.UUID.class) && name.equals("randomUUID")
                    || owner.isAssignableTo(java.util.Random.class);
        });
    }

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
}
