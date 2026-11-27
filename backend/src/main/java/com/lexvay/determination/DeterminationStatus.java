package com.lexvay.determination;

public enum DeterminationStatus {
    SIGNED("Signed"),
    REOPENED("Reopened");

    private final String label;

    DeterminationStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
