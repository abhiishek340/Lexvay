package com.lexvay.observability;

import com.lexvay.common.LogContext;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/** Adds {@code requestId} to every problem detail the MVC layer writes, whichever handler produced it. */
@RestControllerAdvice
public class ProblemDetailRequestId implements ResponseBodyAdvice<Object> {

    public static final String PROPERTY = "requestId";

    /** For problem details written outside MVC, such as the security entry points. */
    public static ProblemDetail stamp(ProblemDetail problem) {
        String requestId = LogContext.current(LogContext.REQUEST_ID);
        if (requestId != null) {
            problem.setProperty(PROPERTY, requestId);
        }
        return problem;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        return body instanceof ProblemDetail problem ? stamp(problem) : body;
    }
}
