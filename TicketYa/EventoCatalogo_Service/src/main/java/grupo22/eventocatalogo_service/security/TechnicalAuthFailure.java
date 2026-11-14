package grupo22.eventocatalogo_service.security;
/** Controlled technical failure; never a commercial rejection or authorization bypass. */
public class TechnicalAuthFailure extends org.springframework.web.client.RestClientException {
 public enum Reason { AUTHENTICATION,TOKEN_CIRCUIT_OPEN,TOKEN_SATURATED,TOKEN_LOCK_TIMEOUT,TOKEN_UNAVAILABLE }
 private final Reason reason;
 public TechnicalAuthFailure(){this(Reason.AUTHENTICATION);}
 public TechnicalAuthFailure(Reason reason){super("Technical authentication unavailable; operation remains recoverable");this.reason=reason;}
 public Reason reason(){return reason;}
}
