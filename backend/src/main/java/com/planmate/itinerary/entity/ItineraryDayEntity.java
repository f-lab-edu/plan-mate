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
import java.time.LocalDate;

@Entity
@Table(name = "itinerary_days")
public class ItineraryDayEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "itinerary_id", nullable = false)
    private ItineraryEntity itinerary;

    @Column(name = "day_number", nullable = false)
    private int dayNumber;

    @Column(name = "date_label", nullable = false)
    private LocalDate dateLabel;

    @Column(nullable = false, length = 120)
    private String theme;

    protected ItineraryDayEntity() {
    }

    private ItineraryDayEntity(ItineraryEntity itinerary, int dayNumber, LocalDate dateLabel, String theme) {
        this.itinerary = itinerary;
        this.dayNumber = dayNumber;
        this.dateLabel = dateLabel;
        this.theme = theme;
    }

    public static ItineraryDayEntity create(
            ItineraryEntity itinerary,
            int dayNumber,
            LocalDate dateLabel,
            String theme
    ) {
        return new ItineraryDayEntity(itinerary, dayNumber, dateLabel, theme);
    }

    public Long getId() {
        return id;
    }

    public int getDayNumber() {
        return dayNumber;
    }

    public LocalDate getDateLabel() {
        return dateLabel;
    }

    public String getTheme() {
        return theme;
    }

}
