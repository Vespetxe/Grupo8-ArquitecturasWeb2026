package ar.edu.unicen.grupo8.comprasservice.client;

/** Controlled diagnostics only: no remote body, URL, credentials or cause retained. */
public final class RemoteFailure extends org.springframework.web.client.RestClientException {
    public enum Category { RATE_LIMITED, UNAVAILABLE, UNCONFIRMED_REJECTION, RESULT_UNKNOWN, INVALID_RESPONSE }
    private final Category category;
    public RemoteFailure(Category category){super("Remote operation pending: "+category.name());this.category=category;}
    public Category category(){return category;}
}
