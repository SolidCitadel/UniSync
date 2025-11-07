package com.unisync.user.common.repository;

import com.unisync.user.common.entity.CredentialProvider;
import com.unisync.user.common.entity.Credentials;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CredentialsRepository extends JpaRepository<Credentials, Long> {

    Optional<Credentials> findByCognitoSubAndProvider(String cognitoSub, CredentialProvider provider);

    List<Credentials> findAllByCognitoSub(String cognitoSub);

    boolean existsByCognitoSubAndProvider(String cognitoSub, CredentialProvider provider);

    void deleteByCognitoSubAndProvider(String cognitoSub, CredentialProvider provider);
}