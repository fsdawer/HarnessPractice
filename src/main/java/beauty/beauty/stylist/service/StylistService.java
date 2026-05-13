package beauty.beauty.stylist.service;

import beauty.beauty.stylist.dto.ServiceRequest;
import beauty.beauty.stylist.dto.ServiceResponse;
import beauty.beauty.stylist.dto.StylistProfileResponse;
import beauty.beauty.stylist.dto.UpdateStylistProfileRequest;
import beauty.beauty.stylist.dto.WorkingHoursRequest;
import beauty.beauty.stylist.dto.StylistDashboardResponse;
import beauty.beauty.stylist.dto.WorkingHoursResponse;
import java.util.List;

public interface StylistService {
    List<StylistProfileResponse> getStylists(String keyword, String location);

    StylistProfileResponse getStylist(Long stylistId);

    StylistProfileResponse getMyProfile(Long userId);

    StylistProfileResponse updateProfile(Long userId, UpdateStylistProfileRequest updateStylistProfileRequest);


    ServiceResponse addService(Long userId, ServiceRequest request);

    ServiceResponse updateService(Long userId, Long serviceId, ServiceRequest request);

    void deleteService(Long userId, Long serviceId);

    WorkingHoursResponse updateHours(Long userId, WorkingHoursRequest request);

    List<StylistProfileResponse> getNearbyStylists(double lat, double lng, int radius);

    StylistDashboardResponse getDashboard(Long userId);
}
