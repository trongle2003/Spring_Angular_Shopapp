package com.project.shopapp.models;

import com.project.shopapp.services.Product.IProductRedisService;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;


@AllArgsConstructor
@Component
public class ProductListener {

    private static final Logger logger = LoggerFactory.getLogger(ProductListener.class);
    private static IProductRedisService productRedisService;

    public  ProductListener(IProductRedisService productRedisService) {
        this.productRedisService = productRedisService;
    }

    @PrePersist
    public void prePersist(Product product) {
        logger.info("prePersist");
    }

    @PostPersist //save = persis
    public void postPersist(Product product) {
        // Update Redis cache
        logger.info("postPersist");
        productRedisService.clear();
    }

    @PreUpdate
    public void preUpdate(Product product) {
        //ApplicationEventPublisher.instance().publishEvent(event);
        logger.info("preUpdate");
    }

    @PostUpdate
    public void postUpdate(Product product) {
        logger.info("postUpdate");
        try {
            productRedisService.clear();
        } catch (Exception ex) {
            logger.error("Failed to clear Redis cache", ex);

        }
    }

    @PreRemove
    public void preRemove(Product product) {
        //ApplicationEventPublisher.instance().publishEvent(event);
        logger.info("preRemove");
    }

    @PostRemove
    public void postRemove(Product product) {
        // Update Redis cache
        logger.info("postRemove");
        productRedisService.clear();
    }
}
