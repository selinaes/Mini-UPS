
import org.example.TestAmazonClient;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import gpb.UpsAmazon;

public class TestAmazon {
    @Test
    public void testAmazon() throws IOException {
        TestAmazonClient testAmazonClient = new TestAmazonClient("localhost", 34567);
        testAmazonClient.connectToUPS();

        testAmazonClient.sendConnectedWorld();

        UpsAmazon.AUreqPickup pickup = testAmazonClient.generateAUreqPickup(1, 50, 50, 1, 1, 1);
        UpsAmazon.AUcommands.Builder builder = UpsAmazon.AUcommands.newBuilder();
        testAmazonClient.addToAUcommands(builder, pickup);
        testAmazonClient.sendAUcommands(builder.build());

        // Create a CountDownLatch with a count of 1
        CountDownLatch latch = new CountDownLatch(1);

        // Wait for the latch to be counted down, with a timeout
        try {
            latch.await(20, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

}
