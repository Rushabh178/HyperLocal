package com.HyperLocalSystem.app.customer.repository;

import com.HyperLocalSystem.app.customer.entity.CustomerAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, UUID> {

    List<CustomerAddress> findByCustomerIdAndDeletedFalseOrderByDefaultAddressDescUpdatedAtDesc(UUID customerId);

    Optional<CustomerAddress> findByIdAndCustomerIdAndDeletedFalse(UUID id, UUID customerId);

    long countByCustomerIdAndDeletedFalse(UUID customerId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update CustomerAddress a set a.defaultAddress = false where a.customerId = :customerId and a.defaultAddress = true")
    void clearDefault(@Param("customerId") UUID customerId);
}
