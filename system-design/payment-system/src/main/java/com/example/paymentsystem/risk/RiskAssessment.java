package com.example.paymentsystem.risk;

import com.example.paymentsystem.payment.RiskDecision;

public record RiskAssessment(int score, RiskDecision decision, String reason) {
}
