package com.HyperLocalSystem.app;

import com.HyperLocalSystem.app.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppApplicationTests extends IntegrationTest {

	@Test
	void contextLoads() {
	}

	/** db/indexes.sql runs after Hibernate's DDL and adds the GiST indexes JPA can't declare. */
	@Test
	void spatialIndexesAreCreated() {
		Integer gist = jdbc.queryForObject(
				"select count(*) from pg_indexes where indexname = 'idx_merchants_location_gist' and indexdef ilike '%gist%'",
				Integer.class);
		assertThat(gist).isEqualTo(1);
	}

}
