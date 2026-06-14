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
@Table(name = "itinerary_items")
public class ItineraryItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "itinerary_day_id", nullable = false)
    private ItineraryDayEntity day;

    @Column(name = "item_order", nullable = false)
    private int itemOrder;

    @Column(name = "start_time", nullable = false, length = 5)
    private String startTime;

    @Column(name = "end_time", nullable = false, length = 5)
    private String endTime;

    @Column(name = "place_name", nullable = false, length = 120)
    private String placeName;

    @Column(nullable = false, length = 30)
    private String category;

    @Column(name = "area_hint", nullable = false, length = 80)
    private String areaHint;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "cost_min", nullable = false)
    private int costMin;

    @Column(name = "cost_max", nullable = false)
    private int costMax;

    @Column(name = "cost_included", nullable = false, columnDefinition = "TEXT")
    private String costIncluded;

    @Column(name = "parking_required", nullable = false)
    private boolean parkingRequired;

    @Column(name = "parking_cost_min", nullable = false)
    private int parkingCostMin;

    @Column(name = "parking_cost_max", nullable = false)
    private int parkingCostMax;

    @Column(name = "parking_notes", nullable = false, columnDefinition = "TEXT")
    private String parkingNotes;

    @Column(name = "transport_mode", nullable = false, length = 40)
    private String transportMode;

    @Column(name = "transport_from_previous_minutes", nullable = false)
    private int transportFromPreviousMinutes;

    @Column(name = "transport_cost_min", nullable = false)
    private int transportCostMin;

    @Column(name = "transport_cost_max", nullable = false)
    private int transportCostMax;

    @Column(name = "transport_notes", nullable = false, columnDefinition = "TEXT")
    private String transportNotes;

    @Column(name = "why_recommended", nullable = false, columnDefinition = "TEXT")
    private String whyRecommended;

    @Column(name = "needs_verification", nullable = false)
    private boolean needsVerification;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "map_visible", nullable = false)
    private boolean mapVisible;

    protected ItineraryItemEntity() {
    }

    private ItineraryItemEntity(Builder builder) {
        this.day = builder.day;
        this.itemOrder = builder.itemOrder;
        this.startTime = builder.startTime;
        this.endTime = builder.endTime;
        this.placeName = builder.placeName;
        this.category = builder.category;
        this.areaHint = builder.areaHint;
        this.description = builder.description;
        this.costMin = builder.costMin;
        this.costMax = builder.costMax;
        this.costIncluded = builder.costIncluded;
        this.parkingRequired = builder.parkingRequired;
        this.parkingCostMin = builder.parkingCostMin;
        this.parkingCostMax = builder.parkingCostMax;
        this.parkingNotes = builder.parkingNotes;
        this.transportMode = builder.transportMode;
        this.transportFromPreviousMinutes = builder.transportFromPreviousMinutes;
        this.transportCostMin = builder.transportCostMin;
        this.transportCostMax = builder.transportCostMax;
        this.transportNotes = builder.transportNotes;
        this.whyRecommended = builder.whyRecommended;
        this.needsVerification = builder.needsVerification;
        this.latitude = builder.latitude;
        this.longitude = builder.longitude;
        this.mapVisible = builder.mapVisible;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Long getId() {
        return id;
    }

    public int getItemOrder() {
        return itemOrder;
    }

    public String getStartTime() {
        return startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public String getPlaceName() {
        return placeName;
    }

    public String getCategory() {
        return category;
    }

    public String getAreaHint() {
        return areaHint;
    }

    public String getDescription() {
        return description;
    }

    public int getCostMin() {
        return costMin;
    }

    public int getCostMax() {
        return costMax;
    }

    public String getCostIncluded() {
        return costIncluded;
    }

    public boolean isParkingRequired() {
        return parkingRequired;
    }

    public int getParkingCostMin() {
        return parkingCostMin;
    }

    public int getParkingCostMax() {
        return parkingCostMax;
    }

    public String getParkingNotes() {
        return parkingNotes;
    }

    public String getTransportMode() {
        return transportMode;
    }

    public int getTransportFromPreviousMinutes() {
        return transportFromPreviousMinutes;
    }

    public int getTransportCostMin() {
        return transportCostMin;
    }

    public int getTransportCostMax() {
        return transportCostMax;
    }

    public String getTransportNotes() {
        return transportNotes;
    }

    public String getWhyRecommended() {
        return whyRecommended;
    }

    public boolean isNeedsVerification() {
        return needsVerification;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public boolean isMapVisible() {
        return mapVisible;
    }

    public static class Builder {

        private ItineraryDayEntity day;
        private int itemOrder;
        private String startTime;
        private String endTime;
        private String placeName;
        private String category;
        private String areaHint;
        private String description;
        private int costMin;
        private int costMax;
        private String costIncluded;
        private boolean parkingRequired;
        private int parkingCostMin;
        private int parkingCostMax;
        private String parkingNotes;
        private String transportMode;
        private int transportFromPreviousMinutes;
        private int transportCostMin;
        private int transportCostMax;
        private String transportNotes;
        private String whyRecommended;
        private boolean needsVerification;
        private Double latitude;
        private Double longitude;
        private boolean mapVisible;

        public Builder day(ItineraryDayEntity day) {
            this.day = day;
            return this;
        }

        public Builder itemOrder(int itemOrder) {
            this.itemOrder = itemOrder;
            return this;
        }

        public Builder startTime(String startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder endTime(String endTime) {
            this.endTime = endTime;
            return this;
        }

        public Builder placeName(String placeName) {
            this.placeName = placeName;
            return this;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder areaHint(String areaHint) {
            this.areaHint = areaHint;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder costMin(int costMin) {
            this.costMin = costMin;
            return this;
        }

        public Builder costMax(int costMax) {
            this.costMax = costMax;
            return this;
        }

        public Builder costIncluded(String costIncluded) {
            this.costIncluded = costIncluded;
            return this;
        }

        public Builder parkingRequired(boolean parkingRequired) {
            this.parkingRequired = parkingRequired;
            return this;
        }

        public Builder parkingCostMin(int parkingCostMin) {
            this.parkingCostMin = parkingCostMin;
            return this;
        }

        public Builder parkingCostMax(int parkingCostMax) {
            this.parkingCostMax = parkingCostMax;
            return this;
        }

        public Builder parkingNotes(String parkingNotes) {
            this.parkingNotes = parkingNotes;
            return this;
        }

        public Builder transportMode(String transportMode) {
            this.transportMode = transportMode;
            return this;
        }

        public Builder transportFromPreviousMinutes(int transportFromPreviousMinutes) {
            this.transportFromPreviousMinutes = transportFromPreviousMinutes;
            return this;
        }

        public Builder transportCostMin(int transportCostMin) {
            this.transportCostMin = transportCostMin;
            return this;
        }

        public Builder transportCostMax(int transportCostMax) {
            this.transportCostMax = transportCostMax;
            return this;
        }

        public Builder transportNotes(String transportNotes) {
            this.transportNotes = transportNotes;
            return this;
        }

        public Builder whyRecommended(String whyRecommended) {
            this.whyRecommended = whyRecommended;
            return this;
        }

        public Builder needsVerification(boolean needsVerification) {
            this.needsVerification = needsVerification;
            return this;
        }

        public Builder latitude(Double latitude) {
            this.latitude = latitude;
            return this;
        }

        public Builder longitude(Double longitude) {
            this.longitude = longitude;
            return this;
        }

        public Builder mapVisible(boolean mapVisible) {
            this.mapVisible = mapVisible;
            return this;
        }

        public ItineraryItemEntity build() {
            return new ItineraryItemEntity(this);
        }

    }

}
