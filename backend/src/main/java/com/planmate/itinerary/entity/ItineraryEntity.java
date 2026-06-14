package com.planmate.itinerary.entity;

import com.planmate.trip.entity.TripEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "itineraries")
public class ItineraryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private TripEntity trip;

    @Column(name = "source_type", nullable = false, length = 30)
    private String sourceType;

    @Column(name = "source_sample_id", length = 80)
    private String sourceSampleId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(name = "budget_currency", nullable = false, length = 10)
    private String budgetCurrency;

    @Column(name = "budget_total_min", nullable = false)
    private int budgetTotalMin;

    @Column(name = "budget_total_max", nullable = false)
    private int budgetTotalMax;

    @Column(name = "budget_per_person_min", nullable = false)
    private int budgetPerPersonMin;

    @Column(name = "budget_per_person_max", nullable = false)
    private int budgetPerPersonMax;

    @Column(name = "budget_notes", nullable = false, columnDefinition = "TEXT")
    private String budgetNotes;

    @Column(name = "verification_warnings", nullable = false, columnDefinition = "TEXT")
    private String verificationWarnings;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected ItineraryEntity() {
    }

    private ItineraryEntity(
            TripEntity trip,
            String sourceType,
            String sourceSampleId,
            String summary,
            String budgetCurrency,
            int budgetTotalMin,
            int budgetTotalMax,
            int budgetPerPersonMin,
            int budgetPerPersonMax,
            String budgetNotes,
            String verificationWarnings,
            Instant now
    ) {
        this.trip = trip;
        this.sourceType = sourceType;
        this.sourceSampleId = sourceSampleId;
        this.summary = summary;
        this.budgetCurrency = budgetCurrency;
        this.budgetTotalMin = budgetTotalMin;
        this.budgetTotalMax = budgetTotalMax;
        this.budgetPerPersonMin = budgetPerPersonMin;
        this.budgetPerPersonMax = budgetPerPersonMax;
        this.budgetNotes = budgetNotes;
        this.verificationWarnings = verificationWarnings;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static ItineraryEntity create(
            TripEntity trip,
            String sourceType,
            String sourceSampleId,
            String summary,
            String budgetCurrency,
            int budgetTotalMin,
            int budgetTotalMax,
            int budgetPerPersonMin,
            int budgetPerPersonMax,
            String budgetNotes,
            String verificationWarnings,
            Instant now
    ) {
        return new ItineraryEntity(
                trip,
                sourceType,
                sourceSampleId,
                summary,
                budgetCurrency,
                budgetTotalMin,
                budgetTotalMax,
                budgetPerPersonMin,
                budgetPerPersonMax,
                budgetNotes,
                verificationWarnings,
                now
        );
    }

    public Long getId() {
        return id;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getSourceSampleId() {
        return sourceSampleId;
    }

    public String getSummary() {
        return summary;
    }

    public String getBudgetCurrency() {
        return budgetCurrency;
    }

    public int getBudgetTotalMin() {
        return budgetTotalMin;
    }

    public int getBudgetTotalMax() {
        return budgetTotalMax;
    }

    public int getBudgetPerPersonMin() {
        return budgetPerPersonMin;
    }

    public int getBudgetPerPersonMax() {
        return budgetPerPersonMax;
    }

    public String getBudgetNotes() {
        return budgetNotes;
    }

    public String getVerificationWarnings() {
        return verificationWarnings;
    }

}
