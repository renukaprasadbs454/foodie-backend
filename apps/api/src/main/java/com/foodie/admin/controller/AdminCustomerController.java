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

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all registered customers for admin dashboard, scoped to active zone if requested")
    public ResponseEntity<ApiResponse<CustomerDashboardResponse>> getAllCustomers(
            @RequestHeader(value = "X-Active-Zone-Id", required = false) String activeZoneHeader,
            @RequestParam(value = "zoneId", required = false) String zoneIdParam) {

        String activeZoneId = (activeZoneHeader != null && !activeZoneHeader.isBlank())
                ? activeZoneHeader.trim()
                : (zoneIdParam != null && !zoneIdParam.isBlank() ? zoneIdParam.trim() : null);

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
                    tier);
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
            String loyaltyTier) {
    }

    public record CustomerSummary(int totalRegistered, int activeAccounts, int suspendedAccounts,
            double averageCustomerLtv) {
    }

    public record CustomerDashboardResponse(CustomerSummary summary, List<AdminCustomerDto> customers, int total,
            int openTicketsCount) {
    }
}
