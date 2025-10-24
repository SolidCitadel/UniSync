package com.unisync.user.common.repository;

import com.unisync.user.common.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByCognitoSub(String cognitoSub);

    boolean existsByEmail(String email);
}
