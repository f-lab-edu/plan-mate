package com.planmate.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.planmate", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureDependencyTest {

    @ArchTest
    static final ArchRule trip_package_does_not_depend_on_itinerary_package =
            noClasses()
                    .that()
                    .resideInAPackage("com.planmate.trip..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("com.planmate.itinerary..");
}
