package com.foodie.admin.repository;

import com.foodie.admin.entity.City;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CityRepository extends JpaRepository<City, String> {
    Optional<City> findByCityNameIgnoreCase(String cityName);

    boolean existsByCityNameIgnoreCaseAndStatus(String cityName, String status);
}
