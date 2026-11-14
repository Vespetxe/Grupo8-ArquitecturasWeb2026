package grupo22.eventocatalogo_service.observability;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/** After BearerTokenAuthenticationFilter: no JWT parsing or external actor headers. */
public class ServletActorCaptureFilter extends OncePerRequestFilter {
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                               FilterChain chain) throws ServletException, IOException {
        var state = (HttpLogState) request.getAttribute(HttpLogState.ATTRIBUTE);
        if (state != null) {
            var auth=SecurityContextHolder.getContext().getAuthentication();
            state.authenticated(auth);
            if (auth != null && auth.isAuthenticated() && !(auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)) {
                state.authenticatedCorrelation(java.util.Collections.list(request.getHeaders(CorrelationIds.HEADER)));
                response.setHeader(CorrelationIds.HEADER,state.correlationId());
            }
        }
        chain.doFilter(request, response);
    }
}
