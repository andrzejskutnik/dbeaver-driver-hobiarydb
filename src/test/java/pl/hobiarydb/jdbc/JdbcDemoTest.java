package pl.hobiarydb.jdbc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class JdbcDemoTest {
    @Test
    @Timeout(30)
    void verifiesDriverAgainstLoopbackProtocolFixture() throws Exception {
        JdbcDemo.main(new String[0]);
    }
}
