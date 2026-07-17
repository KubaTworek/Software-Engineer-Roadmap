package com.example.ecommerce.cart;

import com.example.ecommerce.auth.AppUser;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "carts")
public class Cart {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CartStatus status = CartStatus.ACTIVE;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CartItem> items = new ArrayList<>();

    protected Cart() {}

    public Cart(AppUser user) {
        this.user = user;
    }

    public Long getId() { return id; }
    public AppUser getUser() { return user; }
    public CartStatus getStatus() { return status; }
    public List<CartItem> getItems() { return items; }

    public void addItem(CartItem item) {
        items.add(item);
        item.setCart(this);
    }

    public void markCheckedOut() {
        this.status = CartStatus.CHECKED_OUT;
    }
}
