package com.example.demeterclient;

public class PlantSuggestion {
    private String name;
    private double feasibilityScore;

    public PlantSuggestion(String name) {
        this.name = name;
        this.feasibilityScore = -1.0; // Default value indicating not yet fetched
    }

    public String getName() {
        return name;
    }

    public double getFeasibilityScore() {
        return feasibilityScore;
    }

    public void setFeasibilityScore(double feasibilityScore) {
        this.feasibilityScore = feasibilityScore;
    }
}