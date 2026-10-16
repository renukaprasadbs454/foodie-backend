package com.foodie.admin.repository;

import com.foodie.admin.entity.City;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

@Repository
public interface CityRepository extends JpaRepository<City, String> {
    List<City> findByCityNameIgnoreCase(String cityName);

    default Optional<City> findFirstCityByCityNameIgnoreCase(String cityName) {
        List<City> cities = findByCityNameIgnoreCase(cityName);
        return (cities != null && !cities.isEmpty()) ? Optional.of(cities.get(0)) : Optional.empty();
    }

    @Query("SELECT COUNT(c) > 0 FROM City c WHERE LOWER(TRIM(c.cityName)) = LOWER(TRIM(:cityName)) AND c.status = :status")
    boolean existsByCityNameIgnoreCaseAndStatus(@Param("cityName") String cityName, @Param("status") String status);
}
