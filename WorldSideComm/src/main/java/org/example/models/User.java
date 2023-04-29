package org.example.models;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;

@Entity
@Table(name = "ups_website_userinfo")
public class User {
    @Id
    @Column(name = "user_id")
    private int ups_id;

    @Column(nullable = true)
    private Integer amazon_id;

    private String user_name;

    private String user_email;

    public int getUps_id() {
        return ups_id;
    }

    public void setUps_id(int ups_id) {
        this.ups_id = ups_id;
    }

    public int getAmazon_id() {
        return amazon_id;
    }

    public void setAmazon_id(int amazon_id) {
        this.amazon_id = amazon_id;
    }

    public String getUser_name() {
        return user_name;
    }

    public void setUser_name(String user_name) {
        this.user_name = user_name;
    }

    public String getUser_email() {
        return user_email;
    }

    public void setUser_email(String user_email) {
        this.user_email = user_email;
    }

    @Override
    public String toString() {
        return "User{" +
                "ups_id=" + ups_id +
                ", amazon_id=" + amazon_id +
                '}';
    }
}
