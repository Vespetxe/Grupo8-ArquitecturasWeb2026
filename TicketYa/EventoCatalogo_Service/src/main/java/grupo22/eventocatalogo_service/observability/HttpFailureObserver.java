package grupo22.eventocatalogo_service.observability;

import jakarta.servlet.http.*;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/** Observes a resolved failure without taking over its response contract. */
@Component
public class HttpFailureObserver implements HandlerExceptionResolver, Ordered {
    @Override public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }
    @Override public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response,
                                                   Object handler, Exception exception) {
        var state = (HttpLogState) request.getAttribute(HttpLogState.ATTRIBUTE);
        // HTTP codes remain the category source for normal handled errors. Only
        // transport timeout/cancellation needs information not present in the code.
        if (state != null) state.transportFailure(exception);
        return null;
    }
}
