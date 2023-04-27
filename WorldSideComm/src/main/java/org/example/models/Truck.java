package org.example.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;

@Entity
@Table(name = "ups_website_truck")
public class Truck {
    @Id
    private int truck_id;

    @Column(nullable = true)
    private int wh_id;

    private int truck_x;

    private int truck_y;
    private String truck_status;


    public String getTruck_status() {
        return truck_status;
    }

    public void setTruck_status(String truck_status) {
        this.truck_status = truck_status;
    }

    public int getTruck_y() {
        return truck_y;
    }

    public void setTruck_y(int truck_y) {
        this.truck_y = truck_y;
    }

    public int getTruck_x() {
        return truck_x;
    }

    public void setTruck_x(int truck_x) {
        this.truck_x = truck_x;
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

    @Override
    public String toString() {
        return "Truck{" +
                "truck_id=" + truck_id +
                ", wh_id=" + wh_id +
                ", truck_x=" + truck_x +
                ", truck_y=" + truck_y +
                ", truck_status='" + truck_status + '\'' +
                '}';
    }
}
