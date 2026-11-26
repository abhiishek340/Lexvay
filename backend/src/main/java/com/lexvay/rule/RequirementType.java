package com.lexvay.rule;

public enum RequirementType {
    NUMERIC, SUBJECT, SUPERVISED_HOURS, ACCREDITATION, JURISDICTION_SPECIFIC;

    public boolean isNumeric() {
        return this == NUMERIC || this == SUPERVISED_HOURS;
    }
}
