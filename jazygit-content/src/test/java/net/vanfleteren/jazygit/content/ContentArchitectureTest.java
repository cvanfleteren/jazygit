package net.vanfleteren.jazygit.content;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Set;
import net.vanfleteren.jazygit.AppRules;
import net.vanfleteren.jazygit.state.diff.ScrolledContent;
import net.vanfleteren.jazygit.ui.content.Body;

/**
 * The rules of jazygit-content: those of the whole application, and that its parts depend on each other one way.
 * That no feature draws the content panel, and that the content knows no feature, the compiler sees to (ADR 136).
 */
@AnalyzeClasses(packages = "net.vanfleteren.jazygit.content", importOptions = ImportOption.DoNotIncludeTests.class)
class ContentArchitectureTest {

    @ArchTest
    static final ArchTests appRules = ArchTests.in(AppRules.class);

    /**
     * The parts of the content, each a subpackage of it, depend on each other one way only: the content panel, in
     * {@code content} itself, uses the rich diff, never the other way round.
     */
    @ArchTest
    static final ArchRule partsAreFreeOfCycles =
            slices().matching("net.vanfleteren.jazygit.content.(*)..").should().beFreeOfCycles();

    /**
     * What the content holds and hands around is named: no vavr tuple in a field, a record component, a parameter or
     * a return type. A tuple within a method, as a fold carries, is left to it; so are the lambdas the compiler makes
     * methods of.
     */
    @ArchTest
    static final ArchRule noTuplesInSignatures = classes()
            .should(
                    new ArchCondition<JavaClass>(
                            "hold no io.vavr.Tuple in a field, record component, parameter or return type") {
                        @Override
                        public void check(JavaClass item, ConditionEvents events) {
                            item.getFields().stream()
                                    .filter(field -> !field.getModifiers().contains(JavaModifier.SYNTHETIC))
                                    .filter(field -> holdsTuple(field.getType()))
                                    .forEach(field -> events.add(SimpleConditionEvent.violated(
                                            field, field.getFullName() + " holds a tuple")));
                            item.getCodeUnits().stream()
                                    .filter(unit -> !unit.getModifiers().contains(JavaModifier.SYNTHETIC))
                                    .filter(unit -> holdsTuple(unit.getReturnType())
                                            || unit.getParameterTypes().stream()
                                                    .anyMatch(ContentArchitectureTest::holdsTuple))
                                    .forEach(unit -> events.add(SimpleConditionEvent.violated(
                                            unit, unit.getFullName() + " takes or gives a tuple")));
                        }
                    });

    /**
     * Each kind of content draws itself and says what sets it apart ({@link Body}), so nothing but a body asks which
     * kind of body a value is: a new kind of content adds a file, not a case to each place that tells them apart.
     */
    @ArchTest
    static final ArchRule noneAsksWhichBodyItIs = noClasses()
            .that()
            .doNotImplement(Body.class)
            .should(new ArchCondition<JavaClass>("check whether a value is one kind of body") {
                @Override
                public void check(JavaClass item, ConditionEvents events) {
                    item.getInstanceofChecks().stream()
                            .filter(check -> check.getRawType().isAssignableTo(Body.class)
                                    && !check.getRawType().isEquivalentTo(Body.class))
                            .forEach(check -> events.add(SimpleConditionEvent.satisfied(
                                    check,
                                    item.getName() + " checks for "
                                            + check.getRawType().getSimpleName() + " in "
                                            + check.getSourceCodeLocation())));
                }
            });

    /**
     * The diffs an area shows, which the content panel opens, versions and expands, and so tells apart.
     */
    private static final Set<Class<?>> DIFFS = Set.of(
            ScrolledContent.Diff.class,
            ScrolledContent.Unchanged.class,
            ScrolledContent.CommitChanges.class,
            ScrolledContent.StashChanges.class,
            ScrolledContent.FileChanges.class);

    /**
     * What sets apart content that is no diff, its body says, keys included (ADR 155): nothing in the content asks
     * whether an area shows one kind of it, as the keys of the area asked whether it showed builds.
     */
    @ArchTest
    static final ArchRule noneAsksWhichContentItIs = noClasses()
            .should(new ArchCondition<JavaClass>("check whether what an area shows is one kind of content but a diff") {
                @Override
                public void check(JavaClass item, ConditionEvents events) {
                    item.getInstanceofChecks().stream()
                            .filter(check -> check.getRawType().isAssignableTo(ScrolledContent.class)
                                    && !check.getRawType().isEquivalentTo(ScrolledContent.class)
                                    && DIFFS.stream().noneMatch(check.getRawType()::isEquivalentTo))
                            .forEach(check -> events.add(SimpleConditionEvent.satisfied(
                                    check,
                                    item.getName() + " checks for "
                                            + check.getRawType().getSimpleName() + " in "
                                            + check.getSourceCodeLocation())));
                }
            });

    private static boolean holdsTuple(JavaType type) {
        return type.getAllInvolvedRawTypes().stream()
                .anyMatch(raw -> raw.getPackageName().equals("io.vavr")
                        && raw.getSimpleName().startsWith("Tuple"));
    }
}
