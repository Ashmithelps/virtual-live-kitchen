package com.virtualkitchen.tip;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChefPayoutAccountRepository extends JpaRepository<ChefPayoutAccount, UUID> {

    Optional<ChefPayoutAccount> findByProviderAccountId(String providerAccountId);

    Optional<ChefPayoutAccount> findByProviderProductId(String providerProductId);

    List<ChefPayoutAccount> findByChefIdIn(Collection<UUID> chefIds);
}
