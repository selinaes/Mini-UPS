
import org.example.TestAmazonClient;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class TestAmazon {
    @Test
    public void testAmazon() throws IOException {
        TestAmazonClient testAmazonClient = new TestAmazonClient("localhost", 34567);
        testAmazonClient.connectToUPS();
    }

}
