package ar.edu.unicen.grupo8.comprasservice.security;
/** A technical dependency failure, NEVER a commercial rejection or financial compensation trigger. */
public class TechnicalAuthFailure extends org.springframework.web.client.RestClientException {
 public TechnicalAuthFailure(){super("Technical authentication unavailable; operation remains recoverable");}
}
