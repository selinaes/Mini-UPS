package org.example.models;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;

@Entity
@Table(name = "ups_website_products_in_package")
public class ProductsInPackage {
    @Id
    private int id;

    private int shipment_id;

    private int product_id;

    private String product_description;

    private  int product_quantity;


    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getShipment_id() {
        return shipment_id;
    }

    public void setShipment_id(int shipment_id) {
        this.shipment_id = shipment_id;
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
