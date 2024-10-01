package com.paicbd.module;

import com.paicbd.module.config.CustomFrameHandler;
import com.paicbd.module.smpp.SmppClientManager;
import com.paicbd.smsc.cdr.CdrProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import redis.clients.jedis.JedisCluster;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = SmppClientModuleApplication.class)
class SmppClientModuleApplicationTest {

    @MockBean
    private JedisCluster jedisCluster;

    @MockBean
    private CdrProcessor cdrProcessor;

    @MockBean
    private SmppClientManager smppClientManager;

    @MockBean
    private CustomFrameHandler customFrameHandler;

    @Test
    void contextLoads() {
        // This test will simply load the application context
    }
}
