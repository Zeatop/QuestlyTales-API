package org.tpi.questlytales;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.tpi.questlytales.services.ActionRegistry;
import org.tpi.questlytales.services.DataTypeRegistry;

@SpringBootTest
class QuestlyTalesApplicationTests {

	// Les registries appellent GitHub dans leur constructeur ; on les mocke
	// pour que le contexte démarre sans réseau ni token réel.
	@MockitoBean
	private DataTypeRegistry dataTypeRegistry;

	@MockitoBean
	private ActionRegistry actionRegistry;

	@Test
	void contextLoads() {
	}

}
