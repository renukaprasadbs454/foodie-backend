package com.foodie.admin.controller;

import com.foodie.auth.entity.UserCredential;
import com.foodie.auth.repository.UserCredentialRepository;
import com.foodie.common.dto.ApiResponse;
import com.foodie.user.entity.Customer;
import com.foodie.user.repository.CustomerRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/customers")
@Tag(name = "Admin — Customers")
public class AdminCustomerController {

    private final CustomerRepository customerRepository;
    private final UserCredentialRepository userCredentialRepository;

    public AdminCustomerController(CustomerRepository customerRepository,
            UserCredentialRepository userCredentialRepository) {
        this.customerRepository = customerRepository;
        this.userCredentialRepository = userCredentialRepository;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all registered customers for admin dashboard")
    public ResponseEntity<ApiResponse<CustomerDashboardResponse>> getAllCustomers() {
        List<Customer> customers = customerRepository.findAll();
        List<UserCredential> credentials = userCredentialRepository.findAllById(
                customers.stream().map(Customer::getUserCredentialId).collect(Collectors.toList()));

        Map<UUID, UserCredential> credMap = credentials.stream()
                .collect(Collectors.toMap(UserCredential::getId, c -> c));

        List<AdminCustomerDto> dtos = customers.stream().map(c -> {
            UserCredential cred = credMap.get(c.getUserCredentialId());
            String phone = cred != null && cred.getPhoneNumber() != null ? cred.getPhoneNumber() : "Unknown";
            String email = c.getEmail() != null ? c.getEmail() : (cred != null ? cred.getEmail() : "Unknown");
            boolean isActive = cred == null || cred.isActive();
            String joinedDate = c.getCreatedAt() != null ? c.getCreatedAt().toString().substring(0, 10) : "unknown";

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
                    joinedDate, // lastOrderDate fake
                    "BRONZE");
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
