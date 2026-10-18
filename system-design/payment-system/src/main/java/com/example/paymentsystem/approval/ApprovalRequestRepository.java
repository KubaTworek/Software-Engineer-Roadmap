package com.example.paymentsystem.approval;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, UUID> { }
