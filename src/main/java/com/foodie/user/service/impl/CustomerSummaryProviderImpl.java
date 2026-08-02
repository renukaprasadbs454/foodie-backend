package com.foodie.user.service.impl;

import com.foodie.shared.contract.CustomerSummaryProvider;
import com.foodie.user.repository.CustomerRepository;
import java.util.Optional;
import java.util.UUID;
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
        return customerRepository.findById(customerId)
                .map(c -> new CustomerSummary(c.getId(), c.getFullName(), c.getProfileImageKey()));
    }
}
