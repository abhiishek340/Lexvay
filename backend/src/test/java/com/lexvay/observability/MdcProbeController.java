package com.lexvay.observability;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Test-only endpoint that reports the logging context as the handler thread sees it. */
@RestController
class MdcProbeController {

    @GetMapping("/api/v1/test-support/mdc")
    Map<String, String> mdc() {
        return MDC.getCopyOfContextMap();
    }
}
