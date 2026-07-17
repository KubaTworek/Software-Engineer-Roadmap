package com.example.ecommerce.invoice;

import com.example.ecommerce.order.CustomerOrder;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "invoices", indexes = {
        @Index(name = "idx_invoice_number", columnList = "invoiceNumber", unique = true)
})
public class Invoice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    private CustomerOrder order;

    @Column(nullable = false, unique = true)
    private String invoiceNumber;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal netAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal vatAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal grossAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 4000)
    private String htmlDocument;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceStatus status = InvoiceStatus.ISSUED;

    @Column(nullable = false)
    private Instant issuedAt = Instant.now();

    protected Invoice() {}

    public Invoice(CustomerOrder order, String invoiceNumber, BigDecimal netAmount, BigDecimal vatAmount, BigDecimal grossAmount, String currency, String htmlDocument) {
        this.order = order;
        this.invoiceNumber = invoiceNumber;
        this.netAmount = netAmount;
        this.vatAmount = vatAmount;
        this.grossAmount = grossAmount;
        this.currency = currency;
        this.htmlDocument = htmlDocument;
    }

    public Long getId() { return id; }
    public CustomerOrder getOrder() { return order; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public BigDecimal getNetAmount() { return netAmount; }
    public BigDecimal getVatAmount() { return vatAmount; }
    public BigDecimal getGrossAmount() { return grossAmount; }
    public String getCurrency() { return currency; }
    public String getHtmlDocument() { return htmlDocument; }
    public InvoiceStatus getStatus() { return status; }
    public Instant getIssuedAt() { return issuedAt; }
}
