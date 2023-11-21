package metrics;

import java.util.HashMap;

import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import io.prometheus.client.hotspot.DefaultExports;
import io.restassured.RestAssured;
import io.restassured.config.ObjectMapperConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.path.json.JsonPath;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class Node_Queue_Size_Custom_Metrics {
//    private static final Gauge sessionQueueSize = Gauge.build()
//            .name("selenium_grid_node_session_queue_size")
//            .help("Number of sessions in queue")
//            .labelNames("node_id", "node_uri") // Adding labels for node identification
//            .register();
	
	static String pemFile = "aztldevops_key.pem";
	static String user = "azureuser";
	static String ip = "98.70.1.193";
	static int port = 22;
	static Session session;
	static ChannelExec channel;

    private final String gridUrl = "http://98.70.1.193:32000/graphql"; 

    public static void main(String[] args) throws JSchException, IOException {
        DefaultExports.initialize();
        RestAssured.config = RestAssuredConfig.config()
                .objectMapperConfig(ObjectMapperConfig.objectMapperConfig().defaultObjectMapperType(
                        io.restassured.mapper.ObjectMapperType.GSON));

        Node_Queue_Size_Custom_Metrics app = new Node_Queue_Size_Custom_Metrics();
        app.startMetricUpdates();
    }

    public void startMetricUpdates() throws JSchException, IOException {
        while (true) {
            fetchAndSetSessionQueueSizesForAllNodes();
            try {
                Thread.sleep(60000); // Sleep for 60 seconds
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void fetchAndSetSessionQueueSizesForAllNodes() throws JSchException, IOException {
    	String hubQuery = "{\"query\":\"query Summary { grid { sessionQueueSize } }\"}";
        String nodeQuery = "{\"query\":\"query GetNodes{nodesInfo{nodes{stereotypes maxSession sessionCount }}}\"}";
        
		String hubResponse = RestAssured.given()
                .contentType("application/json")
                .body(hubQuery)
                .post(gridUrl)
                .then()
                .extract()
                .asString();
		JsonPath jsonPath = new JsonPath(hubResponse);
        int gridSessionQueueSize = jsonPath.getInt("data.grid.sessionQueueSize");

		String nodeResponse = RestAssured.given()
                .contentType("application/json")
                .body(nodeQuery)
                .post(gridUrl)
                .then()
                .extract()
                .asString();
        JSONObject obj = new JSONObject(nodeResponse);

        JSONArray nodes = obj.getJSONObject("data").getJSONObject("nodesInfo").getJSONArray("nodes");
        
        Map<String, Object> nodeInfo = new HashMap<>();

        for (int i = 0; i < nodes.length(); i++) {
            JSONObject node = nodes.getJSONObject(i);
            String json = node.getString("stereotypes");
         // Parse the JSON string
            @SuppressWarnings("deprecation")
			JsonParser jsonParser = new JsonParser();
            @SuppressWarnings("deprecation")
			JsonObject jsonObject = jsonParser.parse(json).getAsJsonArray().get(0).getAsJsonObject(); // Assuming it's an array with one element

            String browserName = jsonObject.getAsJsonObject("stereotype").get("browserName").getAsString();
            int maxSessionCount = node.getInt("maxSession");
            int sessionCount = node.getInt("sessionCount");
            
           
            nodeInfo.put("Browser", browserName) ;
            nodeInfo.put("GridQueueSize", gridSessionQueueSize);
            nodeInfo.put("maxSession",maxSessionCount);
            nodeInfo.put("CurrentSessionCount", sessionCount);
                 
            scaleUpPods( nodeInfo);
            
        }
               
    }

	private void scaleUpPods(Map<String, Object> nodeInfo) throws JSchException, IOException {
			    String browser = (String) nodeInfo.get("Browser");
	        	Integer queueSize = (Integer) nodeInfo.get("GridQueueSize");
	        	Integer maxSession = (Integer) nodeInfo.get("maxSession");
	        	Integer nodeSessionCount = (Integer) nodeInfo.get("CurrentSessionCount");
	        	System.out.println(browser+" :gridQsize="+queueSize+" :nodeMaxSession="+maxSession+" :nodeCurSessionCount="+nodeSessionCount);
	        	System.out.println("-----------------------------------------------------------------");
	        	if((nodeSessionCount==maxSession) && queueSize > 0) {
	        		System.out.println("Scale-up pod for "+browser+ " node");
	        		scaleUp(browser,2);
	        	}
	}
	
	private static void connect() throws JSchException {
		JSch sch = new JSch();
		sch.addIdentity(pemFile);
		session = sch.getSession(user, ip, port);
		session.setConfig("StrictHostKeyChecking", "no");
		session.connect();
		channel = (ChannelExec)session.openChannel("exec");
		System.out.println("Connection done");
	}

	public static void scaleUp(String browser, int numOfPods) throws JSchException, IOException {
	    connect();
	    if (browser.equalsIgnoreCase("chrome")) {
	    String output = executeCommand("kubectl scale deployment.apps/selenium-node-chrome --replicas="+numOfPods);
	    System.out.println("Scale-Up Output: " + output);
	    }
	    else if (browser.equalsIgnoreCase("firefox")) {
	    	String output = executeCommand("kubectl scale deployment.apps/selenium-node-firefox --replicas="+numOfPods);
		    System.out.println("Scale-Up Output: " + output);
	    }
	    tearDown();
	}

	public static String executeCommand(String command) throws IOException, JSchException {
	    channel.setCommand(command);
	    
	    InputStream in = channel.getInputStream();
	    channel.connect();

	    BufferedReader reader = new BufferedReader(new InputStreamReader(in));
	    StringBuilder output = new StringBuilder();
	    String line;
	    
	    while((line = reader.readLine()) != null) {
	        output.append(line).append("\n");
	    }

	    reader.close();
	    channel.disconnect();
	    
	    return output.toString();
	}

	
	public static void tearDown() {
		session.disconnect();
	}

}

