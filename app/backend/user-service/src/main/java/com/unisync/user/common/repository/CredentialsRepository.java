package com.unisync.user.common.repository;

import com.unisync.user.common.entity.CredentialProvider;
import com.unisync.user.common.entity.Credentials;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CredentialsRepository extends JpaRepository<Credentials, Long> {

    Optional<Credentials> findByUserIdAndProvider(Long userId, CredentialProvider provider);

    List<Credentials> findAllByUserId(Long userId);

    boolean existsByUserIdAndProvider(Long userId, CredentialProvider provider);

    void deleteByUserIdAndProvider(Long userId, CredentialProvider provider);
}