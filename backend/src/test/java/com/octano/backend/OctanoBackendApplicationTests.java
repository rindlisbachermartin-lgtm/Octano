package com.octano.backend;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class OctanoBackendApplicationTests {

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	DataSource dataSource;

	@Test
	void iniciaConPostgresJdbcYLaPrimeraMigracion() throws Exception {
		// Consultamos PostgreSQL de verdad para comprobar que Flyway creó el esquema.
		assertTrue(jdbc.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = 'octano')",
				Boolean.class));

		// La versión 1 debe estar registrada una sola vez y sin errores.
		assertEquals(1, jdbc.queryForObject(
				"SELECT count(*) FROM public.flyway_schema_history WHERE version = '1' AND success",
				Integer.class));

		// Comprobamos que JDBC se conecta a PostgreSQL.
		try (var conexion = dataSource.getConnection()) {
			assertTrue(conexion.isValid(2));
			assertEquals("PostgreSQL", conexion.getMetaData().getDatabaseProductName());
		}
	}

}
