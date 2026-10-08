package com.foodie.admin.dto;

public class ServiceAreaDto {

    private String id;
    private String areaName;
    private String cityName;
    private String pincode;
    private String coverageStatus; // FULL_COVERAGE, PARTIAL_COVERAGE, UNAVAILABLE
    private Integer totalOutlets;

    public ServiceAreaDto() {}

    public ServiceAreaDto(String id, String areaName, String cityName, String pincode, String coverageStatus, Integer totalOutlets) {
        this.id = id;
        this.areaName = areaName;
        this.cityName = cityName;
        this.pincode = pincode;
        this.coverageStatus = coverageStatus;
        this.totalOutlets = totalOutlets;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAreaName() {
        return areaName;
    }

    public void setAreaName(String areaName) {
        this.areaName = areaName;
    }

    public String getCityName() {
        return cityName;
    }

    public void setCityName(String cityName) {
        this.cityName = cityName;
    }

    public String getPincode() {
        return pincode;
    }

    public void setPincode(String pincode) {
        this.pincode = pincode;
    }

    public String getCoverageStatus() {
        return coverageStatus;
    }

    public void setCoverageStatus(String coverageStatus) {
        this.coverageStatus = coverageStatus;
    }

    public Integer getTotalOutlets() {
        return totalOutlets;
    }

    public void setTotalOutlets(Integer totalOutlets) {
        this.totalOutlets = totalOutlets;
    }
}
