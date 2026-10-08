package com.HyperLocalSystem.app.customer.service;

import com.HyperLocalSystem.app.common.exception.ApiException;
import com.HyperLocalSystem.app.common.exception.NotImplementedException;
import com.HyperLocalSystem.app.common.geo.GeoPoint;
import com.HyperLocalSystem.app.common.geo.GeoUtils;
import com.HyperLocalSystem.app.customer.dto.CustomerDtos.AddressRequest;
import com.HyperLocalSystem.app.customer.dto.CustomerDtos.AddressResponse;
import com.HyperLocalSystem.app.customer.dto.CustomerDtos.ServiceabilityResponse;
import com.HyperLocalSystem.app.customer.entity.CustomerAddress;
import com.HyperLocalSystem.app.customer.repository.CustomerAddressRepository;
import com.HyperLocalSystem.app.security.PhoneNumbers;
import com.HyperLocalSystem.app.spatial.SpatialIndexService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class CustomerAddressService {

    static final int MAX_ADDRESSES = 10;

    private final CustomerAddressRepository addresses;
    private final SpatialIndexService spatialIndex;

    @Transactional(readOnly = true)
    public List<AddressResponse> list(UUID customerId) {
        return addresses.findByCustomerIdAndDeletedFalseOrderByDefaultAddressDescUpdatedAtDesc(customerId)
                .stream().map(CustomerAddressService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AddressResponse get(UUID customerId, UUID addressId) {
        return toResponse(load(customerId, addressId));
    }

    /** The first address a customer saves always becomes the default. */
    @Transactional
    public AddressResponse create(UUID customerId, AddressRequest request) {
        long existing = addresses.countByCustomerIdAndDeletedFalse(customerId);
        if (existing >= MAX_ADDRESSES) {
            throw ApiException.badRequest("ADDRESS_LIMIT_REACHED", "You can save up to " + MAX_ADDRESSES + " addresses");
        }
        boolean makeDefault = existing == 0 || Boolean.TRUE.equals(request.makeDefault());
        if (makeDefault) {
            addresses.clearDefault(customerId);
        }
        CustomerAddress address = new CustomerAddress();
        address.setCustomerId(customerId);
        apply(address, request);
        address.setDefaultAddress(makeDefault);
        return toResponse(addresses.save(address));
    }

    @Transactional
    public AddressResponse update(UUID customerId, UUID addressId, AddressRequest request) {
        if (Boolean.TRUE.equals(request.makeDefault())) {
            addresses.clearDefault(customerId);
        }
        CustomerAddress address = load(customerId, addressId);
        apply(address, request);
        if (Boolean.TRUE.equals(request.makeDefault())) {
            address.setDefaultAddress(true);
        }
        return toResponse(address);
    }

    @Transactional
    public AddressResponse setDefault(UUID customerId, UUID addressId) {
        addresses.clearDefault(customerId);
        CustomerAddress address = load(customerId, addressId);
        address.setDefaultAddress(true);
        return toResponse(address);
    }

    /** Soft delete. If it was the default, the most recently used remaining address takes over. */
    @Transactional
    public void delete(UUID customerId, UUID addressId) {
        CustomerAddress address = load(customerId, addressId);
        boolean wasDefault = address.isDefaultAddress();
        address.setDeleted(true);
        address.setDefaultAddress(false);
        addresses.flush();
        if (wasDefault) {
            addresses.findByCustomerIdAndDeletedFalseOrderByDefaultAddressDescUpdatedAtDesc(customerId)
                    .stream().findFirst()
                    .ifPresent(next -> next.setDefaultAddress(true));
        }
    }

    /**
     * Is this pin inside the 2 km operating radius of at least one restaurant / Kirana?
     * TODO: query merchants with ST_DWithin(location::geography, :pin::geography, ops.serviceRadiusMeters)
     *       once the merchants table exists.
     */
    public ServiceabilityResponse checkServiceability(GeoPoint location) {
        throw new NotImplementedException("Address serviceability check");
    }

    private void apply(CustomerAddress address, AddressRequest request) {
        address.setLabel(request.label());
        address.setCustomLabel(trimToNull(request.customLabel()));
        address.setRecipientName(trimToNull(request.recipientName()));
        address.setRecipientPhone(request.recipientPhone() == null ? null : PhoneNumbers.normalize(request.recipientPhone()));
        address.setHouseNumber(request.houseNumber().trim());
        address.setBuildingName(trimToNull(request.buildingName()));
        address.setStreet(trimToNull(request.street()));
        address.setLandmark(trimToNull(request.landmark()));
        address.setArea(trimToNull(request.area()));
        address.setCity(request.city().trim());
        address.setState(request.state().trim());
        address.setPincode(request.pincode());
        address.setLocation(GeoUtils.toPoint(request.location()));
        address.setH3Index(spatialIndex.cellOf(request.location().lat(), request.location().lng()));
        address.setDeliveryInstructions(trimToNull(request.deliveryInstructions()));
    }

    private CustomerAddress load(UUID customerId, UUID addressId) {
        return addresses.findByIdAndCustomerIdAndDeletedFalse(addressId, customerId)
                .orElseThrow(() -> ApiException.notFound("Address"));
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static AddressResponse toResponse(CustomerAddress a) {
        String fullAddress = Stream.of(a.getHouseNumber(), a.getBuildingName(), a.getStreet(), a.getLandmark(),
                        a.getArea(), a.getCity(), a.getState() + " " + a.getPincode())
                .filter(Objects::nonNull)
                .collect(Collectors.joining(", "));
        return new AddressResponse(a.getId(), a.getLabel(), a.getCustomLabel(), a.getRecipientName(), a.getRecipientPhone(),
                a.getHouseNumber(), a.getBuildingName(), a.getStreet(), a.getLandmark(), a.getArea(), a.getCity(),
                a.getState(), a.getPincode(), fullAddress, GeoUtils.fromPoint(a.getLocation()), a.getH3Index(),
                a.getDeliveryInstructions(), a.isDefaultAddress());
    }
}
