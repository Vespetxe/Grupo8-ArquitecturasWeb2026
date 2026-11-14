package example.microservicio_inventario_reservas.observability;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Collections;

/** Outer servlet boundary. Actor capture occurs separately inside Spring Security. */
public class HttpLoggingFilter extends OncePerRequestFilter {
    private final io.micrometer.tracing.Tracer tracer;
    public HttpLoggingFilter(){this(null);}
    public HttpLoggingFilter(io.micrometer.tracing.Tracer tracer){this.tracer=tracer;}
    @Override protected boolean shouldNotFilterAsyncDispatch() { return false; }
    @Override protected boolean shouldNotFilterErrorDispatch() { return false; }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                               FilterChain chain) throws ServletException, IOException {
        HttpLogState state = (HttpLogState) request.getAttribute(HttpLogState.ATTRIBUTE);
        if (state == null) {
            String id = CorrelationIds.internal(Collections.list(request.getHeaders(CorrelationIds.HEADER)));
            state = new HttpLogState(id, request.getMethod(), request.getRequestURI().substring(request.getContextPath().length()));
            request.setAttribute(HttpLogState.ATTRIBUTE, state);
        }
        state.trace(tracer);
        response.setHeader(CorrelationIds.HEADER, state.correlationId());
        Throwable failure = null;
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException ex) {
            failure = ex;
            state.failure(ex);
            throw ex; // Logging never changes business/security exception handling.
        } finally {
            if (request.isAsyncStarted()) {
                try {
                    request.getAsyncContext().addListener(new CompletionListener(state, response));
                } catch (IllegalStateException alreadyCompleted) {
                    // An async operation can complete between the check and registration.
                    state.finish(response.getStatus(), HttpLogState.Completion.COMPLETED);
                }
            } else {
                boolean cancelled = state.cancelled();
                int status = cancelled && !response.isCommitted() ? 0
                        : failure != null && !response.isCommitted() ? 500 : response.getStatus();
                state.finish(status, cancelled ? HttpLogState.Completion.CANCELLED
                        : failure == null ? HttpLogState.Completion.COMPLETED : HttpLogState.Completion.ERROR);
            }
        }
    }

    private static final class CompletionListener implements AsyncListener {
        private final HttpLogState state;
        private final HttpServletResponse response;
        private CompletionListener(HttpLogState state, HttpServletResponse response) { this.state = state; this.response = response; }
        @Override public void onComplete(AsyncEvent event) {
            state.finish(state.cancelled() && !response.isCommitted() ? 0 : response.getStatus(),
                    state.cancelled() ? HttpLogState.Completion.CANCELLED : HttpLogState.Completion.COMPLETED);
        }
        @Override public void onTimeout(AsyncEvent event) { state.timeout(); }
        @Override public void onError(AsyncEvent event) { if (event.getThrowable() != null) state.failure(event.getThrowable()); }
        @Override public void onStartAsync(AsyncEvent event) { event.getAsyncContext().addListener(this); }
    }
}
