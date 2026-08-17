package com.example.videostreaming.qoe;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface QoeEventRepository extends JpaRepository<QoeEvent, UUID> {}
