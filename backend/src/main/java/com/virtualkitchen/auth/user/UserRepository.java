package com.virtualkitchen.auth.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByPhone(String phone);

    boolean existsByPhone(String phone);

    List<User> findByRoleAndStatusOrderByCreatedAtAsc(Role role, UserStatus status);

    @Query("select u from User u where (:role is null or u.role = :role) order by u.createdAt desc")
    Page<User> search(@Param("role") Role role, Pageable pageable);
}
