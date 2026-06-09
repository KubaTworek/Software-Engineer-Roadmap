package com.example.newsfeed.experiment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional; import java.util.UUID;
public interface ExperimentRepository extends JpaRepository<Experiment, UUID> { Optional<Experiment> findByName(String name); }
