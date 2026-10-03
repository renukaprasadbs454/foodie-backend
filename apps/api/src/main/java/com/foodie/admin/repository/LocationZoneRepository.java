package com.foodie.admin.repository;

import com.foodie.admin.entity.LocationZone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface LocationZoneRepository extends JpaRepository<LocationZone, String> {
    @Query("SELECT COUNT(z) FROM LocationZone z WHERE LOWER(TRIM(z.cityName)) = LOWER(TRIM(:cityName))")
    int countByCityNameIgnoreCase(@Param("cityName") String cityName);
}
