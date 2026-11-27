package com.lexvay.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.regex.Pattern;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.util.UrlPathHelper;

/**
 * Which paths belong to the single-page app and which to the service. The API and the actuator are the
 * service; every other GET is the app: a file from the bundle, or the app shell for a route that has no
 * file behind it.
 */
public final class SpaRoutes {

    private static final UrlPathHelper PATHS = UrlPathHelper.defaultInstance;
    private static final Pattern SERVICE_PATH = Pattern.compile("^/(api|actuator)(/.*)?$");

    /** GET requests for the app. They carry no data, so the shell and its assets are served without sign-in. */
    public static final RequestMatcher APP_REQUESTS = (HttpServletRequest request) ->
            HttpMethod.GET.matches(request.getMethod()) && !isService(PATHS.getPathWithinApplication(request));

    private SpaRoutes() {
    }

    public static boolean isService(String path) {
        return SERVICE_PATH.matcher(path).matches();
    }

    /** True when the last segment has no file extension, so the path names an app route and not a file. */
    public static boolean isRoute(String path) {
        return path.indexOf('.', path.lastIndexOf('/') + 1) < 0;
    }
}
