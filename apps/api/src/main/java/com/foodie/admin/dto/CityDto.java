package com.foodie.admin.dto;

public class CityDto {
    private String id;
    private String cityName;
    private String state;
    private int activeZonesCount;
    private int activeMerchantsCount;
    private String status;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCityName() { return cityName; }
    public void setCityName(String cityName) { this.cityName = cityName; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public int getActiveZonesCount() { return activeZonesCount; }
    public void setActiveZonesCount(int activeZonesCount) { this.activeZonesCount = activeZonesCount; }
    public int getActiveMerchantsCount() { return activeMerchantsCount; }
    public void setActiveMerchantsCount(int activeMerchantsCount) { this.activeMerchantsCount = activeMerchantsCount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
