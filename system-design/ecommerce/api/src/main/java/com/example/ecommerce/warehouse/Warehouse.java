package com.example.ecommerce.warehouse;

import jakarta.persistence.*;

@Entity
@Table(name = "warehouses", indexes = {
        @Index(name = "idx_warehouses_code", columnList = "code", unique = true)
})
public class Warehouse {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    private String country;
    private String city;
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WarehouseStatus status = WarehouseStatus.ACTIVE;

    protected Warehouse() {}

    public Warehouse(String code, String name, String country, String city, String address) {
        this.code = code;
        this.name = name;
        this.country = country;
        this.city = city;
        this.address = address;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getCountry() { return country; }
    public String getCity() { return city; }
    public String getAddress() { return address; }
    public WarehouseStatus getStatus() { return status; }
}
