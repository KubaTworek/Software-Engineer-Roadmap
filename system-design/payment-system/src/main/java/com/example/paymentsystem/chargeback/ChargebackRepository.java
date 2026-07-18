package com.example.paymentsystem.chargeback;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ChargebackRepository extends JpaRepository<Chargeback, UUID> {
}
