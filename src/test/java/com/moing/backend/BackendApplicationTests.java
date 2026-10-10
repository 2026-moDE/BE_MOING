package com.moing.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

// @SpringBootTest도 ApplicationReadyEvent를 발행하므로, 끄지 않으면
// 컨텍스트가 뜰 때마다 서울시에 지역 목록을 받으러 나간다
@SpringBootTest
@TestPropertySource(properties = "seoul.area-sync-on-startup=false")
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
