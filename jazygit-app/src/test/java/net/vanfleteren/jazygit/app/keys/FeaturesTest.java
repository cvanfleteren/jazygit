package net.vanfleteren.jazygit.app.keys;

import static org.junit.jupiter.api.Assertions.*;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.control.Try;
import java.lang.reflect.Field;
import net.vanfleteren.jazygit.ui.keys.Feature;
import org.junit.jupiter.api.Test;

/**
 * Each feature package declares its {@link Feature} once, and {@link Features#ALL} lists it (ADR 127). The feature
 * packages are those of the groups of features (ADR 136): each package right under {@code changes}, {@code branches}
 * and {@code chrome}, and {@code github} as a whole.
 */
class FeaturesTest {

    private static final String ROOT = "net.vanfleteren.jazygit.";

    /**
     * The groups whose packages are features each.
     */
    private static final List<String> GROUPS = List.of("changes", "branches", "chrome");

    /**
     * The group that is one feature.
     */
    private static final String GITHUB = "github";

    /**
     * The top-level feature packages that are no feature: {@code ahead}'s actions are listed by {@code branch} and
     * {@code commit}, {@code layout} has only global keys, and {@code start} has the popups of the start screen.
     */
    private static final Set<String> NO_FEATURE = HashSet.of("changes.ahead", "chrome.layout", "branches.start");

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages(GROUPS.append(GITHUB).map(ROOT::concat).toJavaList());

    /**
     * Each declared feature, with the feature package it is declared in.
     */
    private static List<Tuple2<String, Feature>> declared() {
        return List.ofAll(CLASSES)
                .flatMap(type -> List.ofAll(type.getFields()))
                .filter(field -> field.getRawType().isEquivalentTo(Feature.class)
                        && field.getModifiers().contains(JavaModifier.STATIC))
                .map(field -> Tuple.of(featurePackage(field.getOwner().getPackageName()), value(field)));
    }

    /**
     * The feature package {@code packageName} is in, as {@code changes.stage} or {@code github}; none for the root of a
     * group of features.
     */
    private static String featurePackage(String packageName) {
        String[] parts = packageName.substring(ROOT.length()).split("\\.");
        return parts[0].equals(GITHUB) || parts.length < 2 ? parts[0] : parts[0] + "." + parts[1];
    }

    private static Feature value(JavaField field) {
        return Try.of(() -> {
                    Field reflected = field.reflect();
                    reflected.setAccessible(true);
                    return (Feature) reflected.get(null);
                })
                .get();
    }

    @Test
    void eachFeaturePackageDeclaresOneFeature() {
        List<String> packages = declared().map(Tuple2::_1);
        assertEquals(packages.distinct(), packages, "a package declares more than one feature");
    }

    @Test
    void everyFeatureIsListedOnce() {
        List<Feature> features = declared().map(Tuple2::_2);
        assertTrue(features.forAll(feature -> Features.ALL.count(listed -> listed == feature) == 1));
        assertEquals(features.size(), Features.ALL.size());
    }

    @Test
    void onlyThePackagesThatAreNoFeatureDeclareNone() {
        Set<String> all = HashSet.ofAll(CLASSES)
                .map(JavaClass::getPackageName)
                .map(FeaturesTest::featurePackage)
                .filter(name -> name.contains(".") || name.equals(GITHUB));
        Set<String> withFeature = HashSet.ofAll(declared().map(Tuple2::_1));
        assertEquals(NO_FEATURE, all.diff(withFeature));
    }
}
