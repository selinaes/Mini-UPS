package org.example.models;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;

@Entity
@Table(name = "ups_website_truck")
public class Truck {
    @Id
    private int truck_id;

    private int truck_x;
    private int truck_y;
    private String truck_status;


}
