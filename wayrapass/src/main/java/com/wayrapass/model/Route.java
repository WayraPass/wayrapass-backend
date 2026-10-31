package com.wayrapass.model;
import jakarta.persistence.*; import lombok.*; import java.time.Instant;
@Entity @Table(name="routes") @Getter @Setter @NoArgsConstructor
public class Route {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="institution_id",nullable=false) private Institution institution;
 @Column(nullable=false,unique=true,length=30) private String code;
 @Column(nullable=false,length=150) private String name;
 @Column(nullable=false,length=200) private String origin;
 @Column(nullable=false,length=200) private String destination;
 @Column(length=500) private String description;
 @Column(nullable=false) private int capacity;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private RouteStatus status=RouteStatus.ACTIVE;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 @Column(name="updated_at",nullable=false) private Instant updatedAt;
 @PrePersist void create(){var n=Instant.now();createdAt=n;updatedAt=n;} @PreUpdate void update(){updatedAt=Instant.now();}
}
