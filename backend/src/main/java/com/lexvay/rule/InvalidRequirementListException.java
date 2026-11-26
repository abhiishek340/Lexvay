package com.lexvay.rule;

import java.util.List;

public class InvalidRequirementListException extends RuntimeException {

    private final List<String> errors;

    public InvalidRequirementListException(List<String> errors) {
        super("Requirement list is invalid: " + String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> errors() {
        return errors;
    }
}
