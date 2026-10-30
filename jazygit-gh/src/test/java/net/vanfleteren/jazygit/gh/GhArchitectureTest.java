package net.vanfleteren.jazygit.gh;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import net.vanfleteren.jazygit.CoreRules;
import net.vanfleteren.jazygit.ModelRules;

/**
 * The rules of jazygit-gh: those of the whole core and of the models.
 */
@AnalyzeClasses(packages = "net.vanfleteren.jazygit.gh", importOptions = ImportOption.DoNotIncludeTests.class)
class GhArchitectureTest {

    @ArchTest
    static final ArchTests coreRules = ArchTests.in(CoreRules.class);

    @ArchTest
    static final ArchTests modelRules = ArchTests.in(ModelRules.class);
}
