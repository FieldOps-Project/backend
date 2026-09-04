package com.fieldops;

import com.fieldops.user.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = "spring.autoconfigure.exclude="
		+ "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
		+ "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
		+ "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration")
class FieldopsApiApplicationTests {

	@MockitoBean
	private UserRepository userRepository;

	@MockitoBean
	private JpaMetamodelMappingContext jpaMetamodelMappingContext;

	@Test
	void contextLoads() {
	}

}
