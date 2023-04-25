package org.example.models;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;

@Entity
@Table(name = "ups_website_shipments")
public class Shipment {
    @Id
    private long shipment_id;

    private int truck_id;

    private int wh_id;

    private int dest_x;

    private int dest_y;

    private String shipment_status;



}
