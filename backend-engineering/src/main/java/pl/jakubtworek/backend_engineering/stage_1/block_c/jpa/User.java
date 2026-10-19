package pl.jakubtworek.backend_engineering.stage_1.block_c.jpa;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

/**
 * JPA entity mapped to database table.
 *
 * The dedicated table name keeps this focused lab isolated from the other
 * examples that also model a user.
 */
@Entity(name = "JpaExampleUser")
@Table(
        name = "jpa_users",
        indexes = @Index(name = "idx_jpa_users_last_name_id", columnList = "last_name,id")
)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(nullable = false)
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
        this.firstName = requireText(firstName, "firstName");
        this.lastName = requireText(lastName, "lastName");
        if (age == null || age < 0 || age > 150) {
            throw new IllegalArgumentException("age must be between 0 and 150");
        }
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
        return List.copyOf(orders);
    }

    public void addOrder(String productName) {
        orders.add(new Order(productName, this));
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
