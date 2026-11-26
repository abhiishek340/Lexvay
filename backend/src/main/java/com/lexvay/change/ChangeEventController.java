package com.lexvay.change;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read side of change tracking (D-063). Events are created and re-run in milestone 7. */
@RestController
@RequestMapping("/api/v1/change-events")
public class ChangeEventController {

    private final ChangeEventRepository changes;

    public ChangeEventController(ChangeEventRepository changes) {
        this.changes = changes;
    }

    @GetMapping
    List<ChangeEvent> list() {
        return changes.findAll();
    }
}
