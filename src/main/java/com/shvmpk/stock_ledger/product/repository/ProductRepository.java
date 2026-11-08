package com.shvmpk.stock_ledger.product.repository;

import com.shvmpk.stock_ledger.product.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    List<Product> findAllByActiveTrue();

    List<Product> findAllByCategoryIgnoreCase(String category);

    java.util.Optional<Product> findByName(String name);
}
