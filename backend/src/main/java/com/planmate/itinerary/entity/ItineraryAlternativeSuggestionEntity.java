package com.planmate.itinerary.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "itinerary_alternative_suggestions")
public class ItineraryAlternativeSuggestionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "itinerary_id", nullable = false)
    private ItineraryEntity itinerary;

    @Column(nullable = false, length = 120)
    private String target;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "candidates_text", nullable = false, columnDefinition = "TEXT")
    private String candidatesText;

    protected ItineraryAlternativeSuggestionEntity() {
    }

    private ItineraryAlternativeSuggestionEntity(
            ItineraryEntity itinerary,
            String target,
            String reason,
            String candidatesText
    ) {
        this.itinerary = itinerary;
        this.target = target;
        this.reason = reason;
        this.candidatesText = candidatesText;
    }

    public static ItineraryAlternativeSuggestionEntity create(
            ItineraryEntity itinerary,
            String target,
            String reason,
            String candidatesText
    ) {
        return new ItineraryAlternativeSuggestionEntity(itinerary, target, reason, candidatesText);
    }

    public Long getId() {
        return id;
    }

    public String getTarget() {
        return target;
    }

    public String getReason() {
        return reason;
    }

    public String getCandidatesText() {
        return candidatesText;
    }

}
