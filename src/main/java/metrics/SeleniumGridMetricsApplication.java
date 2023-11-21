package metrics;

import java.util.List;

import io.prometheus.client.Gauge;
import io.prometheus.client.hotspot.DefaultExports;
import io.restassured.RestAssured;
import io.restassured.config.ObjectMapperConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.path.json.JsonPath;

public class SeleniumGridMetricsApplication {
	 private static final Gauge sessionCountMetric = Gauge.build()
	            .name("selenium_grid_session_count")
	            .help("Number of sessions for each node")
	            .labelNames("maxSession", "sessionCount", "browser_name")
	            .register();

	    private final String gridUrl = "http://98.70.1.193:32000/graphql"; // Change to your Selenium Grid URL

	    public static void main(String[] args) {
	        DefaultExports.initialize();
	        RestAssured.config = RestAssuredConfig.config()
	                .objectMapperConfig(ObjectMapperConfig.objectMapperConfig().defaultObjectMapperType(
	                        io.restassured.mapper.ObjectMapperType.GSON));

	        SeleniumGridMetricsApplication app = new SeleniumGridMetricsApplication();
	        app.startMetricUpdates();
	    }

	    public void startMetricUpdates() {
	        while (true) {
	            fetchAndSetSessionCountForAllNodes();
	            try {
	                Thread.sleep(10000); // Sleep for 10 seconds
	            } catch (InterruptedException e) {
	                e.printStackTrace();
	            }
	        }
	    }

	    private void fetchAndSetSessionCountForAllNodes() {
	        String query = "{\"query\":\"query GetNodes{nodesInfo{nodes{stereotypes maxSession sessionCount }}}\"}";

	        String response = RestAssured.given()
	                .contentType("application/json")
	                .body(query)
	                .post(gridUrl)
	                .then()
	                .extract()
	                .asString();

	        JsonPath jsonPath = new JsonPath(response);

	        // Check if the "nodes" list is null or empty
	        List<Object> nodes = jsonPath.getList("data.nodesInfo.nodes");
	        if (nodes == null || nodes.isEmpty()) {
	            System.out.println("No nodes found in the response.");
	            return;
	        }

	        // Iterate over the list and set the metric for each node
	        for (Object nodeInfo : nodes) {
	        	int maxSessionCount = ((JsonPath) nodeInfo).getInt("maxSession");
	            int sessionCountValue = ((JsonPath) nodeInfo).getInt("sessionCount");
	            String stereotypes = ((JsonPath) nodeInfo).getString("stereotypes");

	            // Extract browserName from the stereotypes field
	            String browserName = extractBrowserNameFromStereotypes(stereotypes);

	            //sessionCountMetric.labels(maxSessionCount, sessionCountValue, browserName).set(sessionCountValue);
	            System.out.println("Fetched maxsession & current session count & " + maxSessionCount + ": " + sessionCountValue + ", Browser: " + browserName);
	        }
	    }

	    private String extractBrowserNameFromStereotypes(String stereotypes) {
	        JsonPath jsonPath = new JsonPath(stereotypes);
	        String browserName = jsonPath.getString("stereotype.browserName");
	        return browserName;
	    }
}
