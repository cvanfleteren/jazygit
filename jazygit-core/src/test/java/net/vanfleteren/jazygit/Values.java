package net.vanfleteren.jazygit;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.domain.JavaTypeVariable;
import com.tngtech.archunit.lang.ArchCondition;
import io.vavr.collection.List;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

/**
 * What the architecture rules of the core and of the application take for a value: something that never changes.
 */
public final class Values {

    private Values() {}

    /**
     * The types whose content changes: arrays, the collections of java.util, string builders and atomics.
     */
    public static DescribedPredicate<JavaClass> mutable() {
        return DescribedPredicate.describe("an array", JavaClass::isArray)
                .or(assignableTo(Collection.class))
                .or(assignableTo(Map.class))
                .or(assignableTo(Iterator.class))
                .or(assignableTo(StringBuilder.class))
                .or(assignableTo(StringBuffer.class))
                .or(resideInAPackage("java.util.concurrent.."))
                .as("a type whose content changes");
    }

    /**
     * A field holds a value: a primitive, an enum, a record, one of Vavr's types, one of the JDK's that never
     * change ({@code String}, the boxed primitives, {@code java.time}, a {@code Path}, ...), a function, a type
     * variable, or an interface of jazygit, whose implementations are checked where they are declared.
     */
    public static ArchCondition<JavaField> holdAValue() {
        return ArchCondition.from(
                DescribedPredicate.describe("hold a value that never changes", field -> isValue(field.getType())));
    }

    /**
     * A class extends nothing but {@code Object}, {@code Record} or an enum: it is built of others, rather than
     * extending one.
     */
    public static ArchCondition<JavaClass> extendNothing() {
        return ArchCondition.from(DescribedPredicate.describe(
                "extend nothing but Object, Record or an enum",
                type -> type.getRawSuperclass()
                        .map(parent -> parent.isEquivalentTo(Object.class)
                                || parent.isEquivalentTo(Record.class)
                                || parent.isEquivalentTo(Enum.class)
                                || parent.isEnum())
                        .orElse(true)));
    }

    // The JDK's types, outside java.time, that never change.
    private static final List<Class<?>> VALUES = List.of(
            String.class,
            Boolean.class,
            Character.class,
            Byte.class,
            Short.class,
            Integer.class,
            Long.class,
            Float.class,
            Double.class,
            Class.class,
            java.nio.file.Path.class,
            java.nio.file.attribute.FileTime.class,
            java.util.regex.Pattern.class);

    private static boolean isValue(JavaType type) {
        if (type instanceof JavaTypeVariable<?>) {
            return true;
        }
        JavaClass raw = type.toErasure();
        String pkg = raw.getPackageName();
        return !mutable().test(raw)
                && (raw.isPrimitive()
                        || raw.isEnum()
                        || raw.isAssignableTo(Record.class)
                        || pkg.startsWith("io.vavr")
                        || pkg.equals("java.time")
                        || pkg.equals("java.util.function")
                        || VALUES.exists(raw::isEquivalentTo)
                        || pkg.startsWith("net.vanfleteren.jazygit") && raw.isInterface());
    }
}
