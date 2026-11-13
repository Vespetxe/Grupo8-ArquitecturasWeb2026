package ar.edu.ticketya.identidad.entity;

import jakarta.persistence.*;

@Entity @Table(name="identity_technical_clients")
public class TechnicalClient {
    @Id @Column(length=100) private String clientId;
    @Column(nullable=false,length=512) private String secretHash;
    @Column(nullable=false,length=100) private String audience;
    @Column(nullable=false,length=1000) private String scopes;
    @Column(nullable=false) private boolean enabled=true;
    protected TechnicalClient() {}
    public TechnicalClient(String id, String hash, String audience, String scopes) {
        this.clientId=id; this.secretHash=hash; this.audience=audience; this.scopes=scopes;
    }
    public String getClientId(){return clientId;}
    public String getSecretHash(){return secretHash;}
    public String getAudience(){return audience;}
    public String getScopes(){return scopes;}
    public boolean isEnabled(){return enabled;}
}
