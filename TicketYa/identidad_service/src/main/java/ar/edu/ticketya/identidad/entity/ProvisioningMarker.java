package ar.edu.ticketya.identidad.entity;

import jakarta.persistence.*;

/** Unique marker makes first-administrator provisioning atomic, including concurrent CLI runs. */
@Entity @Table(name="identity_provisioning_markers")
public class ProvisioningMarker {
    @Id @Column(length=40) private String name;
    protected ProvisioningMarker() {}
    public ProvisioningMarker(String name) { this.name=name; }
}
