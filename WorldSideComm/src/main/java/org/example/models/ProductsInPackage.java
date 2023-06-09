package org.example.models;

import jakarta.persistence.*;

@Entity
@Table(name = "ups_website_productsinpackage")
public class ProductsInPackage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne
    @JoinColumn(name = "shipment_id")
    private Shipment shipment;

    private long product_id;

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

    public long getProduct_id() {
        return product_id;
    }

    public void setProduct_id(long product_id) {
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

    @Override
    public String toString() {
        return "ProductsInPackage{" +
                "id=" + id +
                ", shipment=" + shipment +
                ", product_id=" + product_id +
                ", product_description='" + product_description + '\'' +
                ", product_quantity=" + product_quantity +
                '}';
    }
}
