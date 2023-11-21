package metrics;

import io.prometheus.client.Gauge;
import io.prometheus.client.hotspot.DefaultExports;
import io.restassured.RestAssured;
import io.restassured.config.*;
import io.restassured.path.json.*;

public class Queue_Size_Custom_Metrics {
    private static final Gauge sessionQueueSize = Gauge.build()
            .name("selenium_grid_session_queue_size")
            .help("Number of sessions in queue")
            .register();

    private final String gridUrl = "http://98.70.1.193:32000/graphql"; // Change to your Selenium Grid URL

    public static void main(String[] args) {
        DefaultExports.initialize();
        RestAssured.config = RestAssuredConfig.config()
                .objectMapperConfig(ObjectMapperConfig.objectMapperConfig().defaultObjectMapperType(
                		io.restassured.mapper.ObjectMapperType.GSON));

        Queue_Size_Custom_Metrics app = new Queue_Size_Custom_Metrics();
        app.startMetricUpdates();
    }

    public void startMetricUpdates() {
        while (true) {
            int sessionQueueSizeValue = fetchSessionQueueSize();
            sessionQueueSize.set(sessionQueueSizeValue);
            System.out.println("Fetched sessionQueueSize: " + sessionQueueSizeValue);

            try {
                Thread.sleep(60000); // Sleep for 60 seconds
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private int fetchSessionQueueSize() {
        String query = "{\"query\":\"query Summary { grid { sessionQueueSize } }\"}";

        String response = RestAssured.given()
                .contentType("application/json")
                .body(query)
                .post(gridUrl)
                .then()
                .extract()
                .asString();

        JsonPath jsonPath = new JsonPath(response);
        return jsonPath.getInt("data.grid.sessionQueueSize");
    }
}

