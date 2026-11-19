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
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CompoundElement;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.constant.ClassDesc;
import java.net.URI;
import java.util.stream.Stream;
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
            .should(asksWhichKindOf(Body.class, "check whether a value is one kind of body"));

    /**
     * What sets apart content that is no diff, its body says, keys included (ADR 155): nothing in the content asks
     * whether an area shows one kind of it, as the keys of the area asked whether it showed builds.
     */
    @ArchTest
    static final ArchRule noneAsksWhichContentItIs = noClasses()
            .should(asksWhichKindOf(
                    ScrolledContent.Other.class, "check whether what an area shows is one kind of content but a diff"));

    /**
     * Holds for a class that tells one kind of {@code kind} apart from the others, by {@code instanceof} or by a case of
     * a pattern switch. A pattern switch compiles to no {@code instanceof}, but to a {@code typeSwitch} whose
     * arguments are the classes of its cases, which ArchUnit does not import: those are read from the class file.
     */
    private static ArchCondition<JavaClass> asksWhichKindOf(Class<?> kind, String description) {
        return new ArchCondition<>(description) {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                item.getInstanceofChecks().stream()
                        .filter(check -> check.getRawType().isAssignableTo(kind)
                                && !check.getRawType().isEquivalentTo(kind))
                        .forEach(check -> events.add(SimpleConditionEvent.satisfied(
                                check,
                                item.getName() + " checks for "
                                        + check.getRawType().getSimpleName() + " in "
                                        + check.getSourceCodeLocation())));
                switchedOn(item)
                        .filter(label -> kind.isAssignableFrom(label) && !kind.equals(label))
                        .forEach(label -> events.add(SimpleConditionEvent.satisfied(
                                item, item.getName() + " switches on " + label.getSimpleName())));
            }
        };
    }

    /**
     * The classes the cases of the pattern switches of {@code item} are of.
     */
    private static Stream<Class<?>> switchedOn(JavaClass item) {
        return item.getSource().stream().flatMap(source -> {
            ClassModel model = ClassFile.of().parse(bytes(source.getUri()));
            return model.methods().stream()
                    .flatMap(method -> method.code().stream())
                    .flatMap(CompoundElement::elementStream)
                    .filter(InvokeDynamicInstruction.class::isInstance)
                    .map(InvokeDynamicInstruction.class::cast)
                    .filter(ContentArchitectureTest::typeSwitch)
                    .flatMap(indy -> indy.bootstrapArgs().stream())
                    .filter(ClassDesc.class::isInstance)
                    .map(label -> loaded((ClassDesc) label));
        });
    }

    private static boolean typeSwitch(InvokeDynamicInstruction indy) {
        return indy.bootstrapMethod().owner().equals(ClassDesc.of("java.lang.runtime.SwitchBootstraps"))
                && indy.bootstrapMethod().methodName().equals("typeSwitch");
    }

    private static Class<?> loaded(ClassDesc label) {
        String descriptor = label.descriptorString();
        String name = descriptor.substring(1, descriptor.length() - 1).replace('/', '.');
        try {
            return Class.forName(name, false, ContentArchitectureTest.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("A case of a switch is of a class not on the class path: " + name, e);
        }
    }

    private static byte[] bytes(URI uri) {
        try (InputStream in = uri.toURL().openStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean holdsTuple(JavaType type) {
        return type.getAllInvolvedRawTypes().stream()
                .anyMatch(raw -> raw.getPackageName().equals("io.vavr")
                        && raw.getSimpleName().startsWith("Tuple"));
    }
}
