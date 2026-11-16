package net.vanfleteren.jazygit.image;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import net.vanfleteren.jazygit.AppRules;

/**
 * The rules of jazygit-image: those of the whole application. That the pictures know nothing of the application,
 * the compiler sees to.
 */
@AnalyzeClasses(packages = "net.vanfleteren.jazygit.image", importOptions = ImportOption.DoNotIncludeTests.class)
class ImageArchitectureTest {

    @ArchTest
    static final ArchTests appRules = ArchTests.in(AppRules.class);
}
