package cn.iocoder.yudao.module.gym.framework.mobile;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

/** Images cannot set tenant headers. Only the dedicated public GET media path accepts a query selector. */
public class GymPublicMediaTenantFilter extends OncePerRequestFilter {
    private final Pattern path;

    public GymPublicMediaTenantFilter(String prefix) {
        path =
                Pattern.compile(
                        Pattern.quote(prefix) + "/gym/public-media/[1-9][0-9]{0,18}/content");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String uri = request.getRequestURI().substring(request.getContextPath().length());
        String[] selectors = request.getParameterValues("tenantId");
        if (!"GET".equals(request.getMethod())
                || !path.matcher(uri).matches()
                || request.getHeader("tenant-id") != null
                || !validSelector(selectors)) {
            chain.doFilter(request, response);
            return;
        }
        // Existing tenant context/security filters still validate tenant existence, status and
        // expiry.
        chain.doFilter(
                new HttpServletRequestWrapper(request) {
                    @Override
                    public String getHeader(String name) {
                        return "tenant-id".equalsIgnoreCase(name)
                                ? selectors[0]
                                : super.getHeader(name);
                    }

                    @Override
                    public Enumeration<String> getHeaders(String name) {
                        return "tenant-id".equalsIgnoreCase(name)
                                ? Collections.enumeration(List.of(selectors[0]))
                                : super.getHeaders(name);
                    }

                    @Override
                    public Enumeration<String> getHeaderNames() {
                        var names = Collections.list(super.getHeaderNames());
                        names.add("tenant-id");
                        return Collections.enumeration(names);
                    }
                },
                response);
    }

    private static boolean validSelector(String[] values) {
        if (values == null || values.length != 1 || !values[0].matches("[1-9][0-9]{0,18}"))
            return false;
        try {
            return Long.parseLong(values[0]) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
