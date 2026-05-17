package pl.jakubtworek.backend_systems_lab_stage_1.block_c.jpa;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

/**
 * JPA entity mapped to database table.
 *
 * Hibernate will map this class to table "users".
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String firstName;

    private String lastName;

    private Integer age;

    /**
     * Default fetch type for @OneToMany is LAZY.
     *
     * Orders are NOT loaded immediately.
     * Hibernate loads them only when getter is accessed.
     *
     * This may lead to N+1 problem.
     */
    @OneToMany(
            mappedBy = "user",
            cascade = CascadeType.ALL,
            fetch = FetchType.LAZY
    )
    private List<Order> orders = new ArrayList<>();

    protected User() {
        // Required by JPA
    }

    public User(String firstName, String lastName, Integer age) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.age = age;
    }

    public Long getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public Integer getAge() {
        return age;
    }

    public List<Order> getOrders() {
        return orders;
    }
}