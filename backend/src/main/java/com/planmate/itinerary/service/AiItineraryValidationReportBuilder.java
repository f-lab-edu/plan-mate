package com.planmate.itinerary.service;

import com.planmate.itinerary.api.validation.AiItineraryValidationReport;
import com.planmate.itinerary.api.validation.ValidationIssue;
import com.planmate.itinerary.api.validation.ValidationIssueCode;
import java.util.ArrayList;
import java.util.List;

class AiItineraryValidationReportBuilder {

    private final List<ValidationIssue> errors = new ArrayList<>();
    private final List<ValidationIssue> warnings = new ArrayList<>();
    private final List<ValidationIssue> unverifiedConditions = new ArrayList<>();

    void error(
            ValidationIssueCode code,
            String path,
            Integer day,
            Integer sequence,
            String placeId
    ) {
        errors.add(ValidationIssue.of(code, path, day, sequence, placeId));
    }

    void warning(
            ValidationIssue issue
    ) {
        warnings.add(issue);
    }

    void unverifiedCondition(
            ValidationIssue issue
    ) {
        unverifiedConditions.add(issue);
    }

    boolean hasErrors() {
        return !errors.isEmpty();
    }

    AiItineraryValidationReport build() {
        return new AiItineraryValidationReport(
                errors,
                warnings,
                unverifiedConditions
        );
    }
}
