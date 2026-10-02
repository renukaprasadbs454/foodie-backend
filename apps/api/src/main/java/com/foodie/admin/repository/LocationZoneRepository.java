package com.foodie.admin.repository;

import com.foodie.admin.entity.LocationZone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LocationZoneRepository extends JpaRepository<LocationZone, String> {
}
