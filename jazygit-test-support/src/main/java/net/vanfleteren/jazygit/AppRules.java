package net.vanfleteren.jazygit;

import static com.tngtech.archunit.base.DescribedPredicate.anyElementThat;
import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.*;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The rules every module of the application keeps, from lib-lens up to jazygit-app (ADR 136). Each takes them in
 * with {@code ArchTests.in(AppRules.class)} and runs them on its own classes; a rule about classes a module does not
 * have finds nothing there. That a module does not use one above it the compiler sees to. The rules name the types of
 * the state and of the core as text, as this module is below them.
 */
public final class AppRules {

    static final String ROOT = "net.vanfleteren.jazygit";
    static final String MSG = ROOT + ".state.Msg";
    static final String CMD = ROOT + ".state.Cmd";
    static final String POPUP = ROOT + ".state.popup.Popup";
    static final String BODY = ROOT + ".ui.content.Body";
    static final String APP_ERROR = ROOT + ".state.AppError";
    static final String PROGRAM = ROOT + ".state.Program";
    static final String BUG = ROOT + ".state.Failure$Bug";
    static final String START_APP = ROOT + ".app.StartApp";
    static final String EXECUTED_COMMAND = ROOT + ".process.ExecutedCommand";
    static final String GH_BRANCH = ROOT + ".gh.model.GhBranch";
    static final String MARKDOWN_IMAGE = ROOT + ".ui.markdown.MarkdownImage";
    // The updates, the messages, the popups and the model, which are pure; nested classes included.
    static final String PURE =
            ".*\\.(\\w*(Update|Msg|Popup)|Model|Preview|Loadable|AppError|Phrase|Operation|LogEntry|CommandLog)(\\$.*)?";
    // The dialogs of the features, which are views; nested classes included.
    static final String DIALOGS = ".*Dialogs?(\\$.*)?";

    /**
     * The packages in which a feature keeps what it shows in the content panel, its source and its body, which are
     * views as its dialogs are (ADR 156): a {@code content} package in a feature, as {@code github.builds.content};
     * and the panes a group of features draws, in its {@code panes} package, as {@code changes.panes.commits}
     * (ADR 158).
     */
    static final String[] FEATURE_CONTENT = {
        "..jazygit.changes..content..",
        "..jazygit.branches..content..",
        "..jazygit.github..content..",
        "..jazygit.chrome..content..",
        "..jazygit.changes.panes..",
        "..jazygit.branches.panes..",
        "..jazygit.github.panes..",
        "..jazygit.chrome.panes.."
    };

    /**
     * The packages of the features, in their groups.
     */
    static final String[] FEATURES = {
        "..jazygit.changes..", "..jazygit.branches..", "..jazygit.github..", "..jazygit.chrome.."
    };

    /**
     * The packages whose classes are values: the state, the features, the settings, the command line and the lens.
     */
    static final String[] VALUES = {
        "..jazygit.state..",
        "..jazygit.changes..",
        "..jazygit.branches..",
        "..jazygit.github..",
        "..jazygit.chrome..",
        "..jazygit.settings..",
        "..jazygit.app.cli..",
        "..jazygit.lens.."
    };

    /**
     * The packages whose code is pure: the state, the features and the lens.
     */
    static final String[] PURE_PACKAGES = {
        "..jazygit.state..",
        "..jazygit.changes..",
        "..jazygit.branches..",
        "..jazygit.github..",
        "..jazygit.chrome..",
        "..jazygit.lens.."
    };

    /**
     * How the settings file is read and written stays inside the settings.
     */
    @ArchTest
    static final ArchRule onlyTheSettingsReadToml = noClasses()
            .that()
            .resideOutsideOfPackage("..jazygit.settings..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.electronwill.nightconfig..")
            .allowEmptyShould(true);
    /**
     * JSON is read through lib-json's {@code Json} and {@code Decoder}; Parsson, which comes along with it, is
     * not used here (ADR 42).
     */
    @ArchTest
    static final ArchRule nothingUsesParsson = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("jakarta.json..", "org.eclipse.parsson..")
            .allowEmptyShould(true);
    /**
     * YAML is read through lib-yaml's {@code YamlTree}; SnakeYAML, which comes along with it, is not used here
     * (ADR 123).
     */
    @ArchTest
    static final ArchRule nothingUsesSnakeYaml = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.snakeyaml..")
            .allowEmptyShould(true);
    /**
     * How the command line is read stays inside the cli package.
     */
    @ArchTest
    static final ArchRule onlyTheCliUsesPicocli = noClasses()
            .that()
            .resideOutsideOfPackage("..jazygit.app.cli..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("picocli..")
            .allowEmptyShould(true);
    /**
     * The application reaches git through a {@code Git} and its roles, which {@code CliGit} gives for a real
     * repository; the {@code Cli} classes behind the roles are none of its business (ADR 69).
     */
    @ArchTest
    static final ArchRule gitIsReachedThroughItsRoles = noClasses()
            .should()
            .dependOnClassesThat(resideInAPackage("..jazygit.git..")
                    .and(simpleNameStartingWith("Cli"))
                    .and(not(simpleName("CliGit"))))
            .allowEmptyShould(true);
    /**
     * How git is run is the business of its roles (ADR 120): of {@code git.process} the application only sees the
     * errors and the files of a repository's metadata.
     */
    @ArchTest
    static final ArchRule gitKeepsHowItRunsToItself = noClasses()
            .should()
            .dependOnClassesThat(resideInAPackage("..jazygit.git.process..")
                    .and(not(named(Pattern.quote(ROOT + ".git.process.") + "(GitError|GitFailure|HeadFile)(\\$.*)?"))))
            .allowEmptyShould(true);
    /**
     * The application reaches GitHub through a {@code GitHub} and its roles, which {@code CliGitHub} gives for a real
     * repository; the {@code Cli} classes behind the roles are none of its business (ADR 114).
     */
    @ArchTest
    static final ArchRule gitHubIsReachedThroughItsRoles = noClasses()
            .should()
            .dependOnClassesThat(resideInAPackage("..jazygit.gh..")
                    .and(simpleNameStartingWith("Cli"))
                    .and(not(simpleName("CliGitHub"))))
            .allowEmptyShould(true);
    /**
     * How gh is asked is the business of its roles (ADR 120): the GraphQL their queries share, and the process that
     * runs gh. Of {@code gh.process} the application only sees the errors and the token.
     */
    @ArchTest
    static final ArchRule gitHubKeepsHowItAsksToItself = noClasses()
            .should()
            .dependOnClassesThat(resideInAPackage("..jazygit.gh.graphql..")
                    .or(resideInAPackage("..jazygit.gh.process..")
                            .and(not(named(
                                    Pattern.quote(ROOT + ".gh.process.") + "(GhError|GhFailure|GhToken)(\\$.*)?")))))
            .allowEmptyShould(true);
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
            .callMethod(Runtime.class, "exec", String.class)
            .allowEmptyShould(true);
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
            .accessTargetWhere(tellsTheTimeOrChance())
            .allowEmptyShould(true);
    /**
     * What happens, what is asked for and what a dialog is about are values: records, or enums.
     */
    @ArchTest
    static final ArchRule messagesCommandsAndPopupsAreValues = classes()
            .that()
            .areAssignableTo(MSG)
            .or()
            .areAssignableTo(CMD)
            .or()
            .areAssignableTo(POPUP)
            .and()
            .areNotInterfaces()
            .should()
            .beAssignableTo(Record.class)
            .orShould()
            .beAssignableTo(Enum.class)
            .allowEmptyShould(true);
    /**
     * What an area of the content panel shows is a value: a record, so two bodies that show the same are equal, as the
     * content panel draws a body anew only when it changes (ADR 152).
     */
    @ArchTest
    static final ArchRule bodiesAreRecords = classes()
            .that()
            .areAssignableTo(BODY)
            .and()
            .areNotInterfaces()
            .should()
            .beAssignableTo(Record.class)
            .allowEmptyShould(true);
    /**
     * The messages of a feature are a sealed set, so that its update switches over all of them, without a default.
     */
    @ArchTest
    static final ArchRule featureMessagesAreSealed = classes()
            .that()
            .areInterfaces()
            .and()
            .areAssignableTo(MSG)
            .and()
            .doNotHaveFullyQualifiedName(MSG)
            .and()
            .haveSimpleNameEndingWith("Msg")
            .should(ArchCondition.from(DescribedPredicate.describe(
                    "be sealed", type -> type.reflect().isSealed())))
            .allowEmptyShould(true);
    /**
     * The errors of a feature are a sealed set of its own, as its messages are: the core only knows that they say
     * themselves as a phrase, so adding one changes nothing outside the feature.
     */
    @ArchTest
    static final ArchRule featureErrorsAreSealed = classes()
            .that()
            .areInterfaces()
            .and()
            .areAssignableTo(APP_ERROR)
            .and()
            .doNotHaveFullyQualifiedName(APP_ERROR)
            .should(ArchCondition.from(DescribedPredicate.describe(
                    "be sealed", type -> type.reflect().isSealed())))
            .allowEmptyShould(true);
    /**
     * A feature's errors are pure values, as the core's are: saying themselves runs nothing, and reads neither the
     * clock nor chance.
     */
    @ArchTest
    static final ArchRule featureErrorsArePure = noClasses()
            .that()
            .areAssignableTo(APP_ERROR)
            .should()
            .dependOnClassesThat(effects())
            .orShould()
            .accessTargetWhere(tellsTheTimeOrChance())
            .allowEmptyShould(true);
    /**
     * A feature's error is one of its sealed set: none of a feature implements {@code AppError} itself.
     */
    @ArchTest
    static final ArchRule featureErrorsGoThroughTheirSet = classes()
            .that()
            .resideInAnyPackage(FEATURES)
            .and()
            .areNotInterfaces()
            .and()
            .areAssignableTo(APP_ERROR)
            .should(ArchCondition.from(DescribedPredicate.describe(
                    "implement AppError through the sealed error interface of their feature",
                    type -> type.getRawInterfaces().stream()
                            .noneMatch(face -> face.getName().equals(APP_ERROR)))))
            .allowEmptyShould(true);
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
            .haveOnlyPrivateConstructors()
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule updatesKeepNothing = fields().that()
            .areDeclaredInClassesThat()
            .haveSimpleNameEndingWith("Update")
            .should()
            .beStatic()
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule updatesAreStaticMethods = methods()
            .that()
            .areDeclaredInClassesThat()
            .haveSimpleNameEndingWith("Update")
            .and()
            .areDeclaredInClassesThat()
            .areNotInterfaces()
            .should()
            .beStatic()
            .allowEmptyShould(true);
    /**
     * The updates make commands; only the program runs them, and {@code StartApp} those of the start screen (ADR 28).
     * A command may run another, as part of itself. A method reference, {@code Cmd::run}, is an access too, so it
     * counts as a call does.
     */
    @ArchTest
    static final ArchRule onlyTheProgramRunsCommands = noClasses()
            .that()
            .haveNameNotMatching("(" + Pattern.quote(PROGRAM) + "|" + Pattern.quote(START_APP) + ")(\\$.*)?")
            .and()
            .areNotAssignableTo(CMD)
            .should()
            .accessTargetWhere(DescribedPredicate.<JavaAccess<?>>describe(
                    "Cmd.run or GitOperationCmd.perform",
                    access -> access.getTargetOwner().isAssignableTo(CMD)
                            && (access.getName().equals("run")
                                    || access.getName().equals("perform"))))
            .allowEmptyShould(true);
    /**
     * The views and the keys only send messages: they neither make commands nor apply a message themselves. They may
     * read one, as the data it is. A reference, {@code Msg::apply} or a command's {@code ::new}, counts as a call
     * does.
     */
    @ArchTest
    static final ArchRule viewsOnlySendMessages = noClasses()
            .that()
            .resideInAnyPackage("..jazygit.ui..", "..jazygit.content..", "..jazygit.app.keys..")
            .or()
            .haveNameMatching(DIALOGS)
            .or()
            .resideInAnyPackage(FEATURE_CONTENT)
            .or()
            .haveSimpleNameEndingWith("Keys")
            .should()
            .accessTargetWhere(DescribedPredicate.<JavaAccess<?>>describe(
                    "a constructor of a command, or Msg.apply",
                    access -> access.getTargetOwner().isAssignableTo(CMD)
                                    && access.getName().equals(JavaConstructor.CONSTRUCTOR_NAME)
                            || access.getTargetOwner().isAssignableTo(MSG)
                                    && access.getName().equals("apply")))
            .allowEmptyShould(true);
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
            .haveRawReturnType(void.class)
            .allowEmptyShould(true);
    /**
     * Pure code fails with a value, never by throwing. The {@code MatchException} the compiler adds to a switch over a
     * sealed type is never thrown. A constructor reference, {@code IllegalStateException::new}, counts as a call does.
     */
    @ArchTest
    static final ArchRule pureCodeThrowsNothing = noClasses()
            .that()
            .haveNameMatching(PURE)
            .should()
            .accessTargetWhere(DescribedPredicate.<JavaAccess<?>>describe(
                    "a constructor of an exception",
                    access -> access.getName().equals(JavaConstructor.CONSTRUCTOR_NAME)
                            && access.getTargetOwner().isAssignableTo(Throwable.class)
                            && !access.getTargetOwner().isEquivalentTo(MatchException.class)))
            .allowEmptyShould(true);
    /**
     * Nor through {@code Require}, which throws for it. Named as text, as the base is not below test-support.
     */
    @ArchTest
    static final ArchRule pureCodeRequiresNothing = noClasses()
            .that()
            .haveNameMatching(PURE)
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("net.vanfleteren.jazygit.base.Require")
            .allowEmptyShould(true);
    /**
     * The commands report how they failed as messages: none declares an exception.
     */
    @ArchTest
    static final ArchRule commandsDeclareNoExceptions = methods()
            .that()
            .areDeclaredInClassesThat()
            .areAssignableTo(CMD)
            .and()
            .doNotHaveModifier(JavaModifier.SYNTHETIC)
            .should(ArchCondition.from(DescribedPredicate.describe(
                    "declare no exception",
                    method -> method.getThrowsClause().getTypes().stream()
                            .noneMatch(thrown -> thrown.isAssignableTo(Exception.class)))))
            .allowEmptyShould(true);
    /**
     * The model is built of values all the way down: what a field holds never changes either. A {@code Failure.Bug}
     * keeps what a command threw, to report it.
     */
    @ArchTest
    static final ArchRule valuesAreValuesAllTheWayDown = fields().that()
            .areDeclaredInClassesThat()
            .resideInAnyPackage(VALUES)
            .and()
            .areDeclaredInClassesThat()
            .doNotHaveFullyQualifiedName(PROGRAM)
            .and()
            .areDeclaredInClassesThat()
            .doNotHaveFullyQualifiedName(BUG)
            .and()
            .areDeclaredInClassesThat()
            .haveNameNotMatching(DIALOGS)
            .and()
            .areDeclaredInClassesThat()
            .resideOutsideOfPackages(FEATURE_CONTENT)
            .and()
            .doNotHaveModifier(JavaModifier.SYNTHETIC)
            .should(Values.holdAValue())
            .allowEmptyShould(true);
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
            .notHaveRawType(Values.mutable())
            .allowEmptyShould(true);
    /**
     * The model and the updates make new collections, never fill one in. A constructor reference,
     * {@code ArrayList::new}, counts as a call does.
     */
    @ArchTest
    static final ArchRule pureCodeFillsNoCollections = noClasses()
            .that()
            .resideInAnyPackage(PURE_PACKAGES)
            .and()
            .doNotHaveFullyQualifiedName(PROGRAM)
            .and()
            .haveNameNotMatching(DIALOGS)
            .and()
            .resideOutsideOfPackages(FEATURE_CONTENT)
            .should()
            .accessTargetWhere(DescribedPredicate.<JavaAccess<?>>describe(
                    "a constructor of a collection that changes or a StringBuilder",
                    access -> access.getName().equals(JavaConstructor.CONSTRUCTOR_NAME)
                            && Values.mutable().test(access.getTargetOwner())))
            .allowEmptyShould(true);
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
            .resideOutsideOfPackage("..jazygit.app.cli..")
            .should()
            .haveRawReturnType(Optional.class)
            .orShould()
            .haveRawParameterTypes(anyElementThat(equivalentTo(Optional.class)))
            .allowEmptyShould(true);
    /**
     * The model and its features are made of records and interfaces, not of classes that extend each other.
     */
    @ArchTest
    static final ArchRule noClassesExtendOthers = classes()
            .that()
            .resideInAnyPackage(PURE_PACKAGES)
            .and()
            .haveNameNotMatching(DIALOGS)
            .and()
            .resideOutsideOfPackages(FEATURE_CONTENT)
            .should(Values.extendNothing())
            .allowEmptyShould(true);
    /**
     * Only {@code OnGitHub} names a branch for gh, from the branch on its remote, so the name of a local branch is not
     * handed to gh for it (ADR 40). A constructor reference, {@code GhBranch::new}, is an access too, so it counts as
     * a call does.
     */
    @ArchTest
    static final ArchRule onlyOnGitHubNamesBranchesForGh = noClasses()
            .that()
            .doNotHaveFullyQualifiedName(ROOT + ".github.OnGitHub")
            .should()
            .accessTargetWhere(DescribedPredicate.describe(
                    "a constructor of GhBranch",
                    access -> access.getTargetOwner().getName().equals(GH_BRANCH)
                            && access.getName().equals("<init>")))
            .allowEmptyShould(true);
    /**
     * Rendering never talks to git or gh: the views only draw the model, and send messages.
     */
    @ArchTest
    static final ArchRule viewsDoNotRunGitOrGh = noClasses()
            .that()
            .resideInAnyPackage("..jazygit.ui..", "..jazygit.content..", "..jazygit.app.keys..")
            .or()
            .haveNameMatching(DIALOGS)
            .or()
            .resideInAnyPackage(FEATURE_CONTENT)
            .should()
            .dependOnClassesThat(runsGitOrGh())
            .allowEmptyShould(true);
    /**
     * Translated text and the terminal UI belong to the views: the ui, the keys of the app, the dialogs of the
     * features and the app itself.
     */
    @ArchTest
    static final ArchRule onlyViewsUseI18nOrTamboUi = noClasses()
            .that()
            .resideOutsideOfPackages("..jazygit.ui..", "..jazygit.content..", "..jazygit.app.keys..")
            .and()
            .resideOutsideOfPackage(ROOT + ".app")
            .and()
            .haveNameNotMatching(DIALOGS)
            .and()
            .resideOutsideOfPackages(FEATURE_CONTENT)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..jazygit.ui.i18n..", "dev.tamboui..")
            .allowEmptyShould(true);
    /**
     * State changes by making new values, never by changing fields. Only the program, which holds the current
     * model, and the dialogs, which are views, keep what changes.
     */
    @ArchTest
    static final ArchRule fieldsAreFinal = fields().that()
            .areDeclaredInClassesThat()
            .resideInAnyPackage(VALUES)
            .and()
            .areDeclaredInClassesThat()
            .doNotHaveFullyQualifiedName(PROGRAM)
            .and()
            .areDeclaredInClassesThat()
            .haveNameNotMatching(DIALOGS)
            .and()
            .areDeclaredInClassesThat()
            .resideOutsideOfPackages(FEATURE_CONTENT)
            .should()
            .beFinal()
            .allowEmptyShould(true);
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
            .haveRawParameterTypes(anyElementThat(javaUtilCollections()))
            .allowEmptyShould(true);

    private AppRules() {}

    /**
     * The classes whose full name, nested ones with their {@code $}, matches {@code regex}.
     */
    private static DescribedPredicate<JavaClass> named(String regex) {
        return DescribedPredicate.describe(
                "named " + regex, type -> type.getName().matches(regex));
    }

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
                    .filter(type -> !type.getPackageName().startsWith(ROOT))
                    .anyMatch(type ->
                            type.tryGetMethod(method.getName(), parameters).isPresent());
        });
    }

    /**
     * The classes of git and gh, but for their models and the timing of checks, which are values.
     */
    private static DescribedPredicate<JavaClass> gitOrGh() {
        return resideInAnyPackage("..jazygit.git..", "..jazygit.gh..")
                .and(not(resideInAnyPackage("..jazygit.git.model..", "..jazygit.gh.model..", "..jazygit.gh.timing..")));
    }

    private static DescribedPredicate<JavaClass> runsGitOrGh() {
        return gitOrGh()
                .and(simpleNameStartingWith("Cli").or(simpleNameEndingWith("Process")))
                .or(resideInAnyPackage("..jazygit.process").and(simpleName("Exec")))
                .as("run git or gh");
    }

    /**
     * Reading the clock, the environment, the machine's defaults (its time zone, its locale) or chance; a method
     * reference, like {@code Instant::now}, included.
     */
    private static DescribedPredicate<JavaAccess<?>> tellsTheTimeOrChance() {
        return DescribedPredicate.describe("tell the time, read the environment or draw a random number", access -> {
            JavaClass owner = access.getTargetOwner();
            String name = access.getName();
            return owner.getPackageName().equals("java.time") && name.equals("now")
                    || owner.isEquivalentTo(System.class)
                            && Stream.of("currentTimeMillis", "nanoTime", "getenv", "getProperty", "getProperties")
                                    .anyMatch(name::equals)
                    || owner.isEquivalentTo(java.time.ZoneId.class) && name.equals("systemDefault")
                    || owner.isEquivalentTo(java.time.Clock.class) && name.startsWith("system")
                    || owner.isEquivalentTo(java.util.Locale.class) && name.equals("getDefault")
                    || owner.isEquivalentTo(java.util.TimeZone.class) && name.equals("getDefault")
                    || owner.isEquivalentTo(Runtime.class)
                    || owner.isEquivalentTo(ProcessHandle.class)
                    || owner.isEquivalentTo(Math.class) && name.equals("random")
                    || owner.isEquivalentTo(java.util.UUID.class) && name.equals("randomUUID")
                    || owner.isAssignableTo(java.util.random.RandomGenerator.class);
        });
    }

    /**
     * What runs git or gh, or shows something: the views, but for the keys they declare and how a picture in Markdown
     * is found, which are data (they were {@code keys} and {@code image} before the views had them, ADR 136).
     */
    private static DescribedPredicate<JavaClass> effects() {
        return gitOrGh()
                .or(resideInAnyPackage(
                                "..jazygit.process..",
                                "..jazygit.ui..",
                                "..jazygit.content..",
                                "dev.tamboui..",
                                "java.io..",
                                "java.nio.file..")
                        .and(not(resideInAPackage("..jazygit.ui.keys..")))
                        .and(not(named(Pattern.quote(MARKDOWN_IMAGE) + "(\\$.*)?"))))
                .or(JavaClass.Predicates.belongToAnyOf(Thread.class, ProcessBuilder.class, java.time.Clock.class))
                .and(not(named(Pattern.quote(EXECUTED_COMMAND))))
                .as("run git or gh, or show something");
    }
}
