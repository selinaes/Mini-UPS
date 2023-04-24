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
}
