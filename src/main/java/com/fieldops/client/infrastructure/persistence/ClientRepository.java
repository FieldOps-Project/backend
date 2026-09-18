package com.fieldops.client.infrastructure.persistence;

import com.fieldops.client.domain.model.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ClientRepository extends JpaRepository<Client, UUID>, JpaSpecificationExecutor<Client> {
}
