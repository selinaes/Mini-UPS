package org.example.models;

import jakarta.persistence.Column;
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

    @Column(name = "ups_userid", nullable = true)
    private int ups_userid;

    public long getShipment_id() {
        return shipment_id;
    }

    public void setShipment_id(long shipment_id) {
        this.shipment_id = shipment_id;
    }

    public int getTruck_id() {
        return truck_id;
    }

    public void setTruck_id(int truck_id) {
        this.truck_id = truck_id;
    }

    public int getWh_id() {
        return wh_id;
    }

    public void setWh_id(int wh_id) {
        this.wh_id = wh_id;
    }

    public int getDest_x() {
        return dest_x;
    }

    public void setDest_x(int dest_x) {
        this.dest_x = dest_x;
    }

    public String getShipment_status() {
        return shipment_status;
    }

    public void setShipment_status(String shipment_status) {
        this.shipment_status = shipment_status;
    }

    public int getDest_y() {
        return dest_y;
    }

    public void setDest_y(int dest_y) {
        this.dest_y = dest_y;
    }

    public int getUps_userid() {
        return ups_userid;
    }

    public void setUps_userid(int ups_userid) {
        this.ups_userid = ups_userid;
    }
}
