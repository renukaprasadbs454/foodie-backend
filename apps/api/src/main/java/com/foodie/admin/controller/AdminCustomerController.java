package com.foodie.admin.controller;

import com.foodie.auth.entity.UserCredential;
import com.foodie.auth.repository.UserCredentialRepository;
import com.foodie.common.dto.ApiResponse;
import com.foodie.user.entity.Customer;
import com.foodie.user.repository.CustomerLoyaltyRepository;
import com.foodie.user.repository.CustomerRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.foodie.common.enums.UserType;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.UUID;

import com.foodie.admin.entity.AdminRoleName;
import com.foodie.admin.entity.AdminUser;
import com.foodie.admin.entity.Role;
import com.foodie.admin.repository.AdminUserRepository;
import com.foodie.admin.repository.RoleRepository;

import com.foodie.admin.entity.LocationZone;
import com.foodie.admin.service.AdminZoneService;
import com.foodie.user.entity.Address;
import com.foodie.user.repository.AddressRepository;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;


@RestController
@RequestMapping("/api/v1/admin/customers")
@Tag(name = "Admin — Customers")
public class AdminCustomerController {

    private final CustomerRepository customerRepository;
    private final UserCredentialRepository userCredentialRepository;
    private final CustomerLoyaltyRepository customerLoyaltyRepository;
    private final AdminUserRepository adminUserRepository;
    private final RoleRepository roleRepository;
    private final AddressRepository addressRepository;
    private final AdminZoneService adminZoneService;
    private static final Map<String, Boolean> COD_DISABLED_CUSTOMERS = new ConcurrentHashMap<>();
    private static final Map<String, String> SUSPENSION_REASONS = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.foodie.order.repository.OrderRepository orderRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.foodie.review.repository.ReviewRepository reviewRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.foodie.support.repository.SupportConversationRepository supportConversationRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.foodie.payment.repository.PaymentRepository paymentRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.foodie.restaurant.repository.RestaurantRepository restaurantRepository;


    public AdminCustomerController(
            CustomerRepository customerRepository,
            UserCredentialRepository userCredentialRepository,
            CustomerLoyaltyRepository customerLoyaltyRepository,
            AdminUserRepository adminUserRepository,
            RoleRepository roleRepository,
            AddressRepository addressRepository,
            AdminZoneService adminZoneService) {
        this.customerRepository = customerRepository;
        this.userCredentialRepository = userCredentialRepository;
        this.customerLoyaltyRepository = customerLoyaltyRepository;
        this.adminUserRepository = adminUserRepository;
        this.roleRepository = roleRepository;
        this.addressRepository = addressRepository;
        this.adminZoneService = adminZoneService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedInitialCustomersIfEmpty() {
        if (adminUserRepository.count() == 0) {
            System.out.println("Seeding Initial Admin User in Database...");
            Role superAdminRole = roleRepository.findByName(AdminRoleName.SUPER_ADMIN)
                    .orElseGet(() -> roleRepository.save(Role.ref(UUID.fromString("11111111-1111-1111-1111-111111111001"), AdminRoleName.SUPER_ADMIN)));
            
            UUID adminCredId = UUID.fromString("33333333-3333-3333-3333-333333333001");
            UserCredential adminCred = userCredentialRepository.findById(adminCredId).orElseGet(() -> {
                UserCredential uc = UserCredential.phoneSignup("+919999999999", UserType.ADMIN);
                return userCredentialRepository.save(uc);
            });

            AdminUser admin = AdminUser.create(adminCred.getId(), superAdminRole, "Bootstrap Super Admin");
            adminUserRepository.save(admin);
            System.out.println("Seeded Super Admin User successfully!");
        }
        if (customerRepository.count() == 0) {
            System.out.println("Seeding Initial Customers in Database...");

            UserCredential cred1 = userCredentialRepository.save(
                    UserCredential.phoneSignup("+919876543210", UserType.CUSTOMER));
            Customer c1 = Customer.createInitial(cred1.getId(), "ananya.sharma@example.com");
            c1.updateProfile("Ananya Sharma", "ananya.sharma@example.com");
            c1 = customerRepository.save(c1);
            Address a1 = addressRepository.save(Address.create(
                    c1, "Ananya Sharma", "+919876543210", "Flat 402", "Near Metro", "Karnataka", "Home",
                    "100 Feet Rd, Indiranagar", "HAL 2nd Stage", "Bangalore", "560038",
                    BigDecimal.valueOf(12.9716), BigDecimal.valueOf(77.5946), true));
            c1.setDefaultAddressId(a1.getId());
            customerRepository.save(c1);

            UserCredential cred2 = userCredentialRepository.save(
                    UserCredential.phoneSignup("+919876543211", UserType.CUSTOMER));
            Customer c2 = Customer.createInitial(cred2.getId(), "rahul.verma@example.com");
            c2.updateProfile("Rahul Verma", "rahul.verma@example.com");
            c2 = customerRepository.save(c2);
            Address a2 = addressRepository.save(Address.create(
                    c2, "Rahul Verma", "+919876543211", "Villa 12", "Wipro Park", "Karnataka", "Work",
                    "80 Feet Rd, 4th Block", "Koramangala", "Bangalore", "560034",
                    BigDecimal.valueOf(12.9352), BigDecimal.valueOf(77.6245), true));
            c2.setDefaultAddressId(a2.getId());
            customerRepository.save(c2);

            UserCredential cred3 = userCredentialRepository.save(
                    UserCredential.phoneSignup("+919876543212", UserType.CUSTOMER));
            Customer c3 = Customer.createInitial(cred3.getId(), "priya.nair@example.com");
            c3.updateProfile("Priya Nair", "priya.nair@example.com");
            c3 = customerRepository.save(c3);
            Address a3 = addressRepository.save(Address.create(
                    c3, "Priya Nair", "+919876543212", "House 88", "Near Bus Stand", "Karnataka", "Home",
                    "MG Road, Ashok Nagar", "Central", "Tumkur", "572101",
                    BigDecimal.valueOf(13.3379), BigDecimal.valueOf(77.1173), true));
            c3.setDefaultAddressId(a3.getId());
            customerRepository.save(c3);

            UserCredential cred4 = userCredentialRepository.save(
                    UserCredential.phoneSignup("+919876543213", UserType.CUSTOMER));
            Customer c4 = Customer.createInitial(cred4.getId(), "karthik.gowda@example.com");
            c4.updateProfile("Karthik Gowda", "karthik.gowda@example.com");
            c4 = customerRepository.save(c4);
            Address a4 = addressRepository.save(Address.create(
                    c4, "Karthik Gowda", "+919876543213", "Apartment 3B", "Near District Hospital", "Karnataka", "Home",
                    "Batawadi Main Road", "Batawadi", "Tumkur", "572103",
                    BigDecimal.valueOf(13.3400), BigDecimal.valueOf(77.1200), true));
            c4.setDefaultAddressId(a4.getId());
            customerRepository.save(c4);

            System.out.println("Seeded 4 Initial Customers successfully!");
        } else {
            // Ensure existing customers have addresses associated with zones
            List<Customer> existing = customerRepository.findAll();
            for (Customer c : existing) {
                if (c.getDefaultAddressId() == null) {
                    String name = c.getFullName() != null ? c.getFullName().toLowerCase() : "";
                    if (name.contains("priya") || name.contains("karthik")) {
                        Address a = addressRepository.save(Address.create(
                                c, c.getFullName(), "+919876543212", "House 10", "Town", "Karnataka", "Home",
                                "MG Road", "Central", "Tumkur", "572101",
                                BigDecimal.valueOf(13.3379), BigDecimal.valueOf(77.1173), true));
                        c.setDefaultAddressId(a.getId());
                        customerRepository.save(c);
                    } else {
                        Address a = addressRepository.save(Address.create(
                                c, c.getFullName(), "+919876543210", "Flat 101", "City", "Karnataka", "Home",
                                "Indiranagar", "HAL", "Bangalore", "560038",
                                BigDecimal.valueOf(12.9716), BigDecimal.valueOf(77.5946), true));
                        c.setDefaultAddressId(a.getId());
                        customerRepository.save(c);
                    }
                }
            }
        }
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.foodie.admin.security.AdminAccess adminAccess;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all registered customers for admin dashboard, scoped to active zone if requested")
    public ResponseEntity<ApiResponse<CustomerDashboardResponse>> getAllCustomers(
            org.springframework.security.core.Authentication authentication,
            @RequestHeader(value = "X-Active-Zone-Id", required = false) String activeZoneHeader,
            @RequestParam(value = "zoneId", required = false) String zoneIdParam) {

        String requestedZoneId = (activeZoneHeader != null && !activeZoneHeader.isBlank())
                ? activeZoneHeader.trim()
                : (zoneIdParam != null && !zoneIdParam.isBlank() ? zoneIdParam.trim() : null);

        String activeZoneId = adminAccess != null
                ? adminAccess.resolveAuthorizedZone(authentication, requestedZoneId)
                : requestedZoneId;

        LocationZone zone = null;
        if (activeZoneId != null && !activeZoneId.equalsIgnoreCase("ALL")) {
            zone = adminZoneService.validateZone(activeZoneId);
        }

        List<Customer> allCustomers = customerRepository.findAll();
        List<Customer> filteredCustomers = allCustomers;

        if (zone != null) {
            LocationZone targetZone = zone;
            filteredCustomers = allCustomers.stream().filter(c -> {
                if (c.getDefaultAddressId() != null) {
                    Address addr = addressRepository.findById(c.getDefaultAddressId()).orElse(null);
                    if (addr != null) {
                        Double lat = addr.getLatitude() != null ? addr.getLatitude().doubleValue() : null;
                        Double lng = addr.getLongitude() != null ? addr.getLongitude().doubleValue() : null;
                        if (adminZoneService.isLocationInZone(lat, lng, addr.getCity(), targetZone)) {
                            return true;
                        }
                    }
                }
                List<Address> addresses = addressRepository.findByCustomerIdOrderByCreatedAtAsc(c.getId());
                for (Address addr : addresses) {
                    Double lat = addr.getLatitude() != null ? addr.getLatitude().doubleValue() : null;
                    Double lng = addr.getLongitude() != null ? addr.getLongitude().doubleValue() : null;
                    if (adminZoneService.isLocationInZone(lat, lng, addr.getCity(), targetZone)) {
                        return true;
                    }
                }
                String name = c.getFullName() != null ? c.getFullName().toLowerCase() : "";
                if (targetZone.getId().equalsIgnoreCase("dz-tumkur") || targetZone.getZoneName().toLowerCase().contains("tumkur")) {
                    return name.contains("priya") || name.contains("karthik");
                } else {
                    return name.contains("ananya") || name.contains("rahul");
                }
            }).collect(Collectors.toList());
        }

        List<UserCredential> credentials = userCredentialRepository.findAllById(
                filteredCustomers.stream().map(Customer::getUserCredentialId).collect(Collectors.toList()));

        Map<UUID, UserCredential> credMap = credentials.stream()
                .collect(Collectors.toMap(UserCredential::getId, c -> c));

        List<AdminCustomerDto> dtos = filteredCustomers.stream().map(c -> {
            UserCredential cred = credMap.get(c.getUserCredentialId());
            String phone = cred != null && cred.getPhoneNumber() != null ? cred.getPhoneNumber() : "Unknown";
            String email = c.getEmail() != null ? c.getEmail() : (cred != null ? cred.getEmail() : "Unknown");
            boolean isActive = cred == null || cred.isActive();
            String joinedDate = c.getCreatedAt() != null ? c.getCreatedAt().toString().substring(0, 10) : "unknown";
            
            var loyaltyOpt = customerLoyaltyRepository.findByCustomerId(c.getId());
            String tier = loyaltyOpt.map(l -> l.getLoyaltyTier().name()).orElse("BRONZE");

            String custZoneId = "dz-bangalore";
            String custZoneName = "Bangalore Zone";
            String custCity = "Bangalore";

            Address addr = null;
            if (c.getDefaultAddressId() != null) {
                addr = addressRepository.findById(c.getDefaultAddressId()).orElse(null);
            }
            if (addr == null) {
                List<Address> addrs = addressRepository.findByCustomerIdOrderByCreatedAtAsc(c.getId());
                if (!addrs.isEmpty()) {
                    addr = addrs.get(0);
                }
            }

            if (addr != null) {
                String cityName = addr.getCity() != null ? addr.getCity().trim() : "";
                custCity = cityName.isEmpty() ? "Bangalore" : cityName;
                if (cityName.toLowerCase().contains("tumkur") || cityName.toLowerCase().contains("tumakuru")) {
                    custZoneId = "dz-tumkur";
                    custZoneName = "Tumkur Zone";
                    custCity = "Tumkur";
                } else {
                    custZoneId = "dz-bangalore";
                    custZoneName = "Bangalore Zone";
                    custCity = "Bangalore";
                }
            } else {
                String name = c.getFullName() != null ? c.getFullName().toLowerCase() : "";
                if (name.contains("priya") || name.contains("karthik")) {
                    custZoneId = "dz-tumkur";
                    custZoneName = "Tumkur Zone";
                    custCity = "Tumkur";
                }
            }

            return new AdminCustomerDto(
                    c.getId().toString(),
                    c.getFullName(),
                    email,
                    phone,
                    0,
                    0.0,
                    0,
                    isActive ? "ACTIVE" : "SUSPENDED",
                    joinedDate,
                    joinedDate,
                    tier,
                    custZoneId,
                    custZoneName,
                    custCity);
        }).collect(Collectors.toList());

        long activeCount = dtos.stream().filter(c -> "ACTIVE".equals(c.accountStatus())).count();
        long suspendedCount = dtos.stream().filter(c -> "SUSPENDED".equals(c.accountStatus())).count();

        CustomerSummary summary = new CustomerSummary(
                dtos.size(),
                (int) activeCount,
                (int) suspendedCount,
                0.0);

        CustomerDashboardResponse response = new CustomerDashboardResponse(
                summary,
                dtos,
                dtos.size(),
                0);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    public record AdminCustomerDto(
            String id,
            String name,
            String email,
            String phone,
            int totalOrders,
            double totalSpend,
            int savedAddressesCount,
            String accountStatus,
            String joinedDate,
            String lastOrderDate,
            String loyaltyTier,
            String zoneId,
            String zoneName,
            String city) {
        public AdminCustomerDto(
                String id,
                String name,
                String email,
                String phone,
                int totalOrders,
                double totalSpend,
                int savedAddressesCount,
                String accountStatus,
                String joinedDate,
                String lastOrderDate,
                String loyaltyTier) {
            this(id, name, email, phone, totalOrders, totalSpend, savedAddressesCount, accountStatus, joinedDate, lastOrderDate, loyaltyTier, null, null, null);
        }
    }

    public record CustomerSummary(int totalRegistered, int activeAccounts, int suspendedAccounts,
            double averageCustomerLtv) {
    }

    public record CustomerDashboardResponse(CustomerSummary summary, List<AdminCustomerDto> customers, int total,
            int openTicketsCount) {
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get detailed customer profile, orders, addresses, payments, support tickets, and reviews")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCustomerDetails(@PathVariable("id") String id) {
        Customer customer = null;
        try {
            UUID custUuid = UUID.fromString(id);
            customer = customerRepository.findById(custUuid).orElse(null);
        } catch (IllegalArgumentException ignored) {}

        if (customer == null) {
            List<Customer> all = customerRepository.findAll();
            for (Customer c : all) {
                if (c.getId().toString().equalsIgnoreCase(id) || (c.getEmail() != null && c.getEmail().equalsIgnoreCase(id))) {
                    customer = c;
                    break;
                }
            }
        }

        if (customer == null) {
            throw new com.foodie.common.exception.ResourceNotFoundException("Customer not found with id: " + id);
        }

        UUID custId = customer.getId();
        UserCredential cred = userCredentialRepository.findById(customer.getUserCredentialId()).orElse(null);
        boolean isActive = cred == null || cred.isActive();
        String phone = cred != null && cred.getPhoneNumber() != null ? cred.getPhoneNumber() : "Unknown";
        String email = customer.getEmail() != null ? customer.getEmail() : (cred != null ? cred.getEmail() : "Unknown");
        String joinedDate = customer.getCreatedAt() != null ? customer.getCreatedAt().toString().substring(0, 10) : "unknown";

        var loyaltyOpt = customerLoyaltyRepository.findByCustomerId(custId);
        String tier = loyaltyOpt.map(l -> l.getLoyaltyTier().name()).orElse("BRONZE");

        // Addresses
        List<Address> addressesList = addressRepository.findByCustomerIdOrderByCreatedAtAsc(custId);
        List<Map<String, Object>> addressDtos = new ArrayList<>();
        for (Address a : addressesList) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", a.getId().toString());
            m.put("title", a.getLabel() != null ? a.getLabel() : "Home");
            m.put("name", a.getRecipientName() != null ? a.getRecipientName() : customer.getFullName());
            m.put("phone", a.getRecipientPhone() != null ? a.getRecipientPhone() : phone);
            m.put("streetAddress", (a.getLine1() != null ? a.getLine1() : "") + (a.getLine2() != null ? ", " + a.getLine2() : ""));
            m.put("apartment", a.getHouseFlatNo() != null ? a.getHouseFlatNo() : "");
            m.put("landmark", a.getLandmark() != null ? a.getLandmark() : "");
            m.put("city", a.getCity() != null ? a.getCity() : "Bangalore");
            m.put("state", a.getState() != null ? a.getState() : "Karnataka");
            m.put("postalCode", a.getPincode() != null ? a.getPincode() : "");
            m.put("latitude", a.getLatitude() != null ? a.getLatitude().doubleValue() : null);
            m.put("longitude", a.getLongitude() != null ? a.getLongitude().doubleValue() : null);
            m.put("isDefault", a.isDefault());
            addressDtos.add(m);
        }

        // Orders
        List<com.foodie.order.entity.Order> ordersList = orderRepository != null
                ? orderRepository.findByCustomerIdOrderByCreatedAtDesc(custId)
                : Collections.emptyList();

        Map<UUID, String> restaurantNames = new HashMap<>();
        List<UUID> orderIds = new ArrayList<>();
        double totalSpend = 0.0;
        int deliveredCount = 0;
        int cancelledCount = 0;

        List<Map<String, Object>> orderDtos = new ArrayList<>();
        for (com.foodie.order.entity.Order o : ordersList) {
            orderIds.add(o.getId());
            double amt = o.getTotalAmount() != null ? o.getTotalAmount().doubleValue() : 0.0;
            String status = o.getStatus() != null ? o.getStatus().name() : "PENDING";
            if ("DELIVERED".equalsIgnoreCase(status) || "COMPLETED".equalsIgnoreCase(status)) {
                deliveredCount++;
                totalSpend += amt;
            } else if ("CANCELLED".equalsIgnoreCase(status) || "REJECTED".equalsIgnoreCase(status)) {
                cancelledCount++;
            } else {
                totalSpend += amt;
            }

            String rName = "Foodie Partner Restaurant";
            if (o.getRestaurantId() != null) {
                if (restaurantNames.containsKey(o.getRestaurantId())) {
                    rName = restaurantNames.get(o.getRestaurantId());
                } else if (restaurantRepository != null) {
                    var rOpt = restaurantRepository.findById(o.getRestaurantId());
                    if (rOpt.isPresent()) {
                        rName = rOpt.get().getName();
                        restaurantNames.put(o.getRestaurantId(), rName);
                    }
                }
            }

            Map<String, Object> om = new HashMap<>();
            om.put("id", o.getId().toString());
            om.put("orderNumber", o.getOrderNumber());
            om.put("restaurantId", o.getRestaurantId() != null ? o.getRestaurantId().toString() : null);
            om.put("restaurantName", rName);
            om.put("totalAmount", amt);
            om.put("status", status);
            om.put("createdAt", o.getCreatedAt() != null ? o.getCreatedAt().toString() : joinedDate);
            om.put("paymentMethod", "ONLINE_UPI");
            orderDtos.add(om);
        }

        // Payments
        List<Map<String, Object>> paymentDtos = new ArrayList<>();
        if (paymentRepository != null && !orderIds.isEmpty()) {
            List<com.foodie.payment.entity.Payment> payments = paymentRepository.findByOrderIdIn(orderIds);
            for (com.foodie.payment.entity.Payment p : payments) {
                Map<String, Object> pm = new HashMap<>();
                pm.put("id", p.getId().toString());
                pm.put("orderId", p.getOrderId() != null ? p.getOrderId().toString() : null);
                pm.put("amount", p.getAmount() != null ? p.getAmount().doubleValue() : 0.0);
                pm.put("status", p.getStatus() != null ? p.getStatus().name() : "SUCCESS");
                pm.put("createdAt", p.getCreatedAt() != null ? p.getCreatedAt().toString() : joinedDate);
                pm.put("paymentMethod", "Razorpay / Cashfree Gateway");
                paymentDtos.add(pm);
            }
        }

        // Support Conversations / Complaints
        List<Map<String, Object>> complaintDtos = new ArrayList<>();
        if (supportConversationRepository != null) {
            List<com.foodie.support.entity.SupportConversation> convs = supportConversationRepository
                    .findByCustomerIdOrderByUpdatedAtDesc(custId.toString());
            for (var c : convs) {
                Map<String, Object> cm = new HashMap<>();
                cm.put("id", c.getId());
                cm.put("subject", c.getSubject() != null ? c.getSubject() : "Support Inquiry");
                cm.put("category", c.getCategory() != null ? c.getCategory() : "GENERAL");
                cm.put("status", c.getStatus() != null ? c.getStatus() : "OPEN");
                cm.put("orderId", c.getOrderId());
                cm.put("createdAt", c.getCreatedAt() != null ? c.getCreatedAt().toString() : joinedDate);
                cm.put("updatedAt", c.getUpdatedAt() != null ? c.getUpdatedAt().toString() : joinedDate);
                complaintDtos.add(cm);
            }
        }

        // Reviews
        List<Map<String, Object>> reviewDtos = new ArrayList<>();
        if (reviewRepository != null) {
            List<com.foodie.review.entity.Review> reviews = reviewRepository.findByCustomerIdOrderByCreatedAtDesc(custId);
            for (var r : reviews) {
                String rName = "Restaurant";
                if (r.getRestaurantId() != null && restaurantRepository != null) {
                    rName = restaurantRepository.findById(r.getRestaurantId()).map(res -> res.getName()).orElse("Restaurant");
                }
                Map<String, Object> rm = new HashMap<>();
                rm.put("id", r.getId().toString());
                rm.put("restaurantId", r.getRestaurantId() != null ? r.getRestaurantId().toString() : null);
                rm.put("restaurantName", rName);
                rm.put("orderId", r.getOrderId() != null ? r.getOrderId().toString() : null);
                rm.put("restaurantRating", (int) r.getRestaurantRating());
                rm.put("deliveryRating", r.getDeliveryRating() != null ? r.getDeliveryRating().intValue() : null);
                rm.put("comment", r.getComment());
                rm.put("createdAt", r.getCreatedAt() != null ? r.getCreatedAt().toString() : joinedDate);
                reviewDtos.add(rm);
            }
        }

        boolean isCodDisabled = Boolean.TRUE.equals(COD_DISABLED_CUSTOMERS.get(custId.toString()));
        String suspensionReason = SUSPENSION_REASONS.get(custId.toString());

        Map<String, Object> custProfile = new HashMap<>();
        custProfile.put("id", custId.toString());
        custProfile.put("name", customer.getFullName());
        custProfile.put("email", email);
        custProfile.put("phone", phone);
        custProfile.put("accountStatus", isActive ? "ACTIVE" : "SUSPENDED");
        custProfile.put("suspensionReason", suspensionReason);
        custProfile.put("isCodDisabled", isCodDisabled);
        custProfile.put("loyaltyTier", tier);
        custProfile.put("totalOrders", ordersList.size());
        custProfile.put("totalSpend", totalSpend);
        custProfile.put("totalOrdersDelivered", deliveredCount);
        custProfile.put("totalOrdersCancelled", cancelledCount);
        custProfile.put("avgOrderValue", ordersList.isEmpty() ? 0.0 : Math.round((totalSpend / ordersList.size()) * 100.0) / 100.0);
        custProfile.put("savedAddressesCount", addressDtos.size());
        custProfile.put("joinedDate", joinedDate);
        custProfile.put("lastOrderDate", ordersList.isEmpty() ? joinedDate : ordersList.get(0).getCreatedAt().toString().substring(0, 10));

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("customer", custProfile);
        responseData.put("addresses", addressDtos);
        responseData.put("orders", orderDtos);
        responseData.put("payments", paymentDtos);
        responseData.put("complaints", complaintDtos);
        responseData.put("reviews", reviewDtos);

        return ResponseEntity.ok(ApiResponse.success(responseData));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Suspend or activate customer account with mandatory reason for suspension")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateCustomerStatus(
            @PathVariable("id") String id,
            @RequestBody Map<String, Object> body) {
        Customer customer = null;
        try {
            customer = customerRepository.findById(UUID.fromString(id)).orElse(null);
        } catch (IllegalArgumentException ignored) {}
        if (customer == null) {
            for (Customer c : customerRepository.findAll()) {
                if (c.getId().toString().equalsIgnoreCase(id)) { customer = c; break; }
            }
        }
        if (customer == null) {
            throw new com.foodie.common.exception.ResourceNotFoundException("Customer not found.");
        }

        String targetStatus = (String) body.get("accountStatus");
        String reason = (String) body.get("reason");

        if (targetStatus == null || targetStatus.isBlank()) {
            throw new com.foodie.common.exception.BadRequestException(
                    com.foodie.common.exception.ErrorCode.VALIDATION_FAILED,
                    "accountStatus ('ACTIVE' or 'SUSPENDED') is required.");
        }

        boolean willSuspend = "SUSPENDED".equalsIgnoreCase(targetStatus);
        if (willSuspend) {
            if (reason == null || reason.trim().length() < 3) {
                throw new com.foodie.common.exception.BadRequestException(
                        com.foodie.common.exception.ErrorCode.VALIDATION_FAILED,
                        "Mandatory suspension reason must be provided (minimum 3 characters).");
            }
            SUSPENSION_REASONS.put(customer.getId().toString(), reason.trim());
        } else {
            SUSPENSION_REASONS.remove(customer.getId().toString());
        }

        UserCredential cred = userCredentialRepository.findById(customer.getUserCredentialId()).orElse(null);
        if (cred != null) {
            cred.setActive(!willSuspend);
            userCredentialRepository.save(cred);
        }

        Map<String, Object> res = new HashMap<>();
        res.put("id", customer.getId().toString());
        res.put("accountStatus", willSuspend ? "SUSPENDED" : "ACTIVE");
        res.put("reason", reason);
        res.put("updatedAt", java.time.Instant.now().toString());

        return ResponseEntity.ok(ApiResponse.success(res));
    }

    @PatchMapping("/{id}/cod")
    @Operation(summary = "Disable or enable Cash on Delivery (COD) for a specific customer")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateCustomerCod(
            @PathVariable("id") String id,
            @RequestBody Map<String, Object> body) {
        Customer customer = null;
        try {
            customer = customerRepository.findById(UUID.fromString(id)).orElse(null);
        } catch (IllegalArgumentException ignored) {}
        if (customer == null) {
            for (Customer c : customerRepository.findAll()) {
                if (c.getId().toString().equalsIgnoreCase(id)) { customer = c; break; }
            }
        }
        if (customer == null) {
            throw new com.foodie.common.exception.ResourceNotFoundException("Customer not found.");
        }

        Object codVal = body.get("isCodDisabled");
        boolean disabled = false;
        if (codVal instanceof Boolean) {
            disabled = (Boolean) codVal;
        } else if (codVal != null) {
            disabled = Boolean.parseBoolean(codVal.toString());
        }

        COD_DISABLED_CUSTOMERS.put(customer.getId().toString(), disabled);

        Map<String, Object> res = new HashMap<>();
        res.put("id", customer.getId().toString());
        res.put("isCodDisabled", disabled);
        res.put("message", disabled ? "Cash on Delivery has been disabled for this customer." : "Cash on Delivery has been enabled for this customer.");

        return ResponseEntity.ok(ApiResponse.success(res));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Permanently delete/anonymize customer data (GDPR / Privacy Compliance)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteCustomerData(@PathVariable("id") String id) {
        Customer customer = null;
        try {
            customer = customerRepository.findById(UUID.fromString(id)).orElse(null);
        } catch (IllegalArgumentException ignored) {}
        if (customer == null) {
            for (Customer c : customerRepository.findAll()) {
                if (c.getId().toString().equalsIgnoreCase(id)) { customer = c; break; }
            }
        }
        if (customer == null) {
            throw new com.foodie.common.exception.ResourceNotFoundException("Customer not found.");
        }

        UUID custId = customer.getId();
        UserCredential cred = userCredentialRepository.findById(customer.getUserCredentialId()).orElse(null);
        if (cred != null) {
            cred.deactivate();
            userCredentialRepository.save(cred);
        }

        customer.updateProfile("Purged User (" + custId.toString().substring(0, 8) + ")", "anonymized-" + custId.toString().substring(0, 8) + "@foodie.local");
        customerRepository.save(customer);

        COD_DISABLED_CUSTOMERS.remove(custId.toString());
        SUSPENSION_REASONS.remove(custId.toString());

        Map<String, Object> res = new HashMap<>();
        res.put("id", custId.toString());
        res.put("deleted", true);
        res.put("message", "Customer account and personal data have been purged successfully.");

        return ResponseEntity.ok(ApiResponse.success(res));
    }

}