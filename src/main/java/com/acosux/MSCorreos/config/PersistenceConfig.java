package com.acosux.MSCorreos.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Configuración de persistencia para MSCorreos.
 *
 * <p>Spring Boot 3 con spring-boot-starter-data-jpa autoconfigura automáticamente:</p>
 * <ul>
 *   <li>DataSource (HikariCP) usando propiedades spring.datasource.*</li>
 *   <li>EntityManagerFactory con Hibernate 6</li>
 *   <li>JpaTransactionManager</li>
 * </ul>
 *
 * <p>La configuración de la base de datos se realiza en application.properties
 * bajo las propiedades {@code spring.datasource.*}.</p>
 */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = "com.acosux.MSCorreos.repositories")
public class PersistenceConfig {
    // Spring Boot autoconfigura DataSource, EntityManagerFactory y TransactionManager
    // usando las propiedades spring.datasource.* de application.properties
}
