package com.crowdpulse.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "community_updates")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommunityUpdate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long placeId;

    private Integer reportedQueueLength;

    private Integer throughputPerMin;

    private String queueStatus;

    @Column(columnDefinition = "TEXT")
    private String note;

    private LocalDateTime createdAt;
}