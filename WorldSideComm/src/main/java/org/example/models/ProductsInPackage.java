package org.example.models;

import jakarta.persistence.*;

@Entity
@Table(name = "ups_website_productsinpackage")
public class ProductsInPackage {
    @Id
    private int id;

    @ManyToOne
    @JoinColumn(name = "shipment_id")
    private Shipment shipment;

    private int product_id;

    private String product_description;

    private  int product_quantity;


    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Shipment getShipment() {
        return shipment;
    }

    public void setShipment(Shipment shipment) {
        this.shipment = shipment;
    }

    public int getProduct_id() {
        return product_id;
    }

    public void setProduct_id(int product_id) {
        this.product_id = product_id;
    }

    public String getProduct_description() {
        return product_description;
    }

    public void setProduct_description(String product_description) {
        this.product_description = product_description;
    }

    public int getProduct_quantity() {
        return product_quantity;
    }

    public void setProduct_quantity(int product_quantity) {
        this.product_quantity = product_quantity;
    }
}
