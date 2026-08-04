package com.foodie.user.service;

import com.foodie.user.dto.request.AddAddressRequestDto;
import com.foodie.user.dto.request.UpdateProfileRequestDto;
import com.foodie.user.dto.response.AddressResponseDto;
import com.foodie.user.dto.response.CustomerProfileResponseDto;
import com.foodie.user.dto.response.FileUploadResponseDto;
import java.util.List;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface CustomerService {

    CustomerProfileResponseDto getMyProfile(UUID userCredentialId);

    CustomerProfileResponseDto updateMyProfile(UUID userCredentialId, UpdateProfileRequestDto request);

    AddressResponseDto addAddress(UUID userCredentialId, AddAddressRequestDto request);

    List<AddressResponseDto> listAddresses(UUID userCredentialId);

    void removeAddress(UUID userCredentialId, UUID addressId);

    FileUploadResponseDto uploadProfileImage(UUID userCredentialId, MultipartFile file);
}
