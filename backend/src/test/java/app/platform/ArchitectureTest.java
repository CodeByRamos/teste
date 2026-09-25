package app.platform;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Business rules stay framework-free and independent of any data source. Engines can be tested without
 * a database, and OpenDB can be replaced or joined by another source by writing a new adapter.
 */
@AnalyzeClasses(packages = "app.platform", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String[] DOMAIN = {
            "app.platform.hardware..", "app.platform.compatibility..", "app.platform.recommendation..",
            "app.platform.pricing..", "app.platform.catalog..", "app.platform.intake..", "app.platform.builds..",
            "app.platform.visualization.."};

    @ArchTest
    static final ArchRule domainDoesNotDependOnFrameworks = noClasses().that().resideInAnyPackage(DOMAIN)
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "tools.jackson..", "jakarta..", "java.sql..");

    @ArchTest
    static final ArchRule domainDoesNotDependOnAdaptersOrApi = noClasses().that().resideInAnyPackage(DOMAIN)
            .should().dependOnClassesThat().resideInAnyPackage("app.platform.infra..", "app.platform.api..", "app.platform.config..");

    @ArchTest
    static final ArchRule onlyTheOpenDbAdapterKnowsOpenDbRecords = noClasses().that().resideOutsideOfPackages(
                    "app.platform.infra.opendb..", "app.platform.config..", "app.platform.api..")
            .should().dependOnClassesThat().resideInAPackage("app.platform.infra.opendb..");
}
