package com.planmate.itinerary.entity;

import com.planmate.trip.entity.TripEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "itinerary_generations")
public class ItineraryGenerationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private TripEntity trip;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ItineraryGenerationStatus status;

    @Column(nullable = false, length = 80)
    private String promptVersion;

    @Column(length = 60)
    private String provider;

    @Column(length = 120)
    private String model;

    @Column(nullable = false, length = 80)
    private String schemaVersion;

    @Column(length = 64)
    private String requestFingerprint;

    @Column
    private Long latencyMillis;

    @Column(length = 80)
    private String failureCode;

    @Column(length = 500)
    private String failureReason;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected ItineraryGenerationEntity() {
    }

    private ItineraryGenerationEntity(
            TripEntity trip,
            String promptVersion,
            String provider,
            String model,
            String schemaVersion,
            String requestFingerprint,
            Instant now
    ) {
        this.trip = trip;
        this.status = ItineraryGenerationStatus.CREATED;
        this.promptVersion = promptVersion;
        this.provider = provider;
        this.model = model;
        this.schemaVersion = schemaVersion;
        this.requestFingerprint = requestFingerprint;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static ItineraryGenerationEntity create(TripEntity trip, String promptVersion, Instant now) {
        return new ItineraryGenerationEntity(trip, promptVersion, null, null, "grounded-itinerary-draft-v1", null, now);
    }

    public static ItineraryGenerationEntity create(
            TripEntity trip,
            String promptVersion,
            String provider,
            String model,
            String schemaVersion,
            String requestFingerprint,
            Instant now
    ) {
        return new ItineraryGenerationEntity(
                trip,
                promptVersion,
                provider,
                model,
                schemaVersion,
                requestFingerprint,
                now
        );
    }

    public void markCollecting(Instant now) {
        status = ItineraryGenerationStatus.COLLECTING_CANDIDATES;
        failureReason = null;
        failureCode = null;
        latencyMillis = null;
        updatedAt = now;
    }

    public void markPlanning(Instant now) {
        status = ItineraryGenerationStatus.PLANNING;
        failureReason = null;
        failureCode = null;
        latencyMillis = null;
        updatedAt = now;
    }

    public void markReady(Instant now) {
        status = ItineraryGenerationStatus.READY_FOR_PLANNING;
        failureReason = null;
        updatedAt = now;
    }

    public void markValidating(Instant now) {
        status = ItineraryGenerationStatus.VALIDATING;
        updatedAt = now;
    }

    public void markCompleted(Instant now) {
        status = ItineraryGenerationStatus.COMPLETED;
        failureReason = null;
        failureCode = null;
        updatedAt = now;
    }

    public void markFailed(String failureCode, String reason, Instant now) {
        status = ItineraryGenerationStatus.FAILED;
        this.failureCode = failureCode;
        failureReason = reason;
        updatedAt = now;
    }

    public void recordProviderLatency(Long latencyMillis) {
        this.latencyMillis = latencyMillis;
    }

    public Long getId() {
        return id;
    }

    public TripEntity getTrip() {
        return trip;
    }

    public ItineraryGenerationStatus getStatus() {
        return status;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public Long getLatencyMillis() {
        return latencyMillis;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
