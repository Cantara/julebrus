package no.cantara.julebrus.mcp;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/**
 * Guards /mcp with a shared bearer token. Without a configured token the endpoint
 * answers 404, so a deployment that never set one exposes nothing.
 */
public final class McpTokenFilter extends OncePerRequestFilter {

    private final McpProperties props;

    public McpTokenFilter(McpProperties props) {
        this.props = props;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (!props.enabled()) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String auth = req.getHeader("Authorization");
        String presented = auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7) ? auth.substring(7).trim() : "";
        if (!MessageDigest.isEqual(presented.getBytes(StandardCharsets.UTF_8), props.token().getBytes(StandardCharsets.UTF_8))) {
            res.setHeader("WWW-Authenticate", "Bearer");
            res.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        chain.doFilter(req, res);
    }
}
