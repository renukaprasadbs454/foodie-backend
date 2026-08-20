package com.foodie.user.service.impl;

import com.foodie.shared.contract.CustomerSummaryProvider;
import com.foodie.user.repository.CustomerRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerSummaryProviderImpl implements CustomerSummaryProvider {

    private final CustomerRepository customerRepository;

    public CustomerSummaryProviderImpl(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CustomerSummary> findByCustomerId(UUID customerId) {
        return customerRepository.findById(customerId).map(this::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, CustomerSummary> findByCustomerIdIn(Collection<UUID> customerIds) {
        if (customerIds == null || customerIds.isEmpty()) {
            return Map.of();
        }
        return customerRepository.findAllById(customerIds).stream()
                .collect(Collectors.toMap(com.foodie.user.entity.Customer::getId, this::toSummary, (a, b) -> a));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findCustomerIdsByNameContaining(String name) {
        if (name == null || name.isBlank()) {
            return List.of();
        }
        return customerRepository.findByFullNameContainingIgnoreCase(name.trim()).stream()
                .map(com.foodie.user.entity.Customer::getId)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CustomerSummary> findByUserCredentialId(UUID userCredentialId) {
        return customerRepository.findByUserCredentialId(userCredentialId).map(this::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> findUserCredentialIdByCustomerId(UUID customerId) {
        return customerRepository.findById(customerId).map(c -> c.getUserCredentialId());
    }

    private CustomerSummary toSummary(com.foodie.user.entity.Customer c) {
        return new CustomerSummary(c.getId(), c.getFullName(), c.getProfileImageKey());
    }
}
