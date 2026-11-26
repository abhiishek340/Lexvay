package com.lexvay.rule;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/jurisdictions")
public class JurisdictionController {

    private final JurisdictionRepository jurisdictions;

    public JurisdictionController(JurisdictionRepository jurisdictions) {
        this.jurisdictions = jurisdictions;
    }

    @GetMapping
    List<Jurisdiction> list() {
        return jurisdictions.findAll();
    }
}
