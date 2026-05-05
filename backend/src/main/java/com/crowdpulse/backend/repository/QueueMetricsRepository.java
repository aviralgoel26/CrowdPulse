package com.crowdpulse.backend.repository;

import com.crowdpulse.backend.model.QueueMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface QueueMetricsRepository extends JpaRepository<QueueMetrics, Long> {

    List<QueueMetrics> findTop50ByPlaceIdOrderByTimestampDesc(Long placeId);
}