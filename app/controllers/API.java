package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import play.libs.F;
import play.libs.Json;
import play.libs.ws.WSClient;
import play.libs.ws.WSRequest;
import play.libs.ws.WSResponse;
import play.mvc.BodyParser;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Results;

import javax.inject.Inject;

public class API extends Controller {

    @Inject
    WSClient ws;

    private static final String API_VERSION = "0.2.1";
    private Config config = ConfigFactory.load();

    public F.Promise<Result> points(String n, String s, String w, String e) {
        WSRequest request = ws.url(getSearchUrl());
        F.Promise<WSResponse> responsePromise = request.post(getRequestBody(n, s, w, e));

        play.Logger.debug(String.format("N - : %s, E - : %s, W - : %s, E - : %s", n, s, w, e));

        return responsePromise.map(response -> ok(getJsonPoints(response.asJson())));
    }


    @BodyParser.Of(BodyParser.Json.class)
    public F.Promise<Result> addPoint() {

        JsonNode json = request().body().asJson();
        String name = json.findPath("name").textValue();
        double latitude = json.get("location").get("lat").asDouble();
        double longitude = json.get("location").get("lon").asDouble();
        float rating = json.get("rating").floatValue();

        String id_token = json.findPath("access_token").textValue();

        ObjectNode newPoint = Json.newObject();
        newPoint.put("name", name);
        newPoint.put("rating", rating);
        newPoint.put("opening_hours", "");
        newPoint.putObject("location")
                .put("lat", latitude)
                .put("lon", longitude);

        play.Logger.debug(String.format("Name: %s, Rating: %f ,lat: %f, lon: %f; \n Json: %s",
                                            name, rating, latitude, longitude, newPoint));

        WSRequest request = ws.url(getAddPointUrl()).setContentType("application/json");
        F.Promise<WSResponse> responsePromise;

        if (isValidToken(id_token)) {
            responsePromise = request.post(newPoint.toString());

            return responsePromise.map(response -> {
                String result = response.getBody();
                play.Logger.debug(result);
                return ok(response.asJson());
            });

        } else {
            responsePromise = null;
            ObjectNode status = Json.newObject();
            status.put("status", "error");
            status.put("point", newPoint);
            status.put("reason", "invalid token");
            return responsePromise.map(wsResponse -> unauthorized(status));
        }
    }

    public F.Promise<Result> testValidate(String token) {
        WSRequest request = ws.url(getValidateTokenUrl(token));

        F.Promise<WSResponse> responsePromise = request.get();
        return responsePromise.map(response -> {
            play.Logger.debug(response.getBody());
            if (response.getStatus() == 200) {
                return ok(response.getBody());
            } else {
                return unauthorized("fail");
            }
        });
    }

    public F.Promise<Result> status() {
        ObjectNode status = Json.newObject();
        status.put("status", "ok");
        status.put("version", API_VERSION);
        F.Promise<JsonNode> responsePromise = F.Promise.promise(() -> status);
        return responsePromise.map(Results::ok);
    }




    private JsonNode getJsonPoints(JsonNode response) {
        ObjectNode result = Json.newObject();
        result.put("result", "ok");
        ArrayNode responseArray = (ArrayNode) response.get("hits").get("hits");
        ArrayNode pointsArray = Json.newArray();
        for (JsonNode item : responseArray) {
            pointsArray.add(item.get("_source"));
        }
        result.putArray("points").addAll(pointsArray);
        return result;
    }

    private String getRequestBody(String n, String s, String w, String e) {
        return String.format("{\n" +
                        " \"size\" : 1000," +   // number of documents (points)\n
                        " \"query\":{\n" +
                        "    \"bool\" : {\n" +
                        "        \"must\" : {\n" +
                        "            \"match_all\" : {}\n" +             // search query (empty match all documents)\n
                        "         },\n" +
                        "        \"filter\" : {\n" +
                        "            \"geo_bounding_box\" : {\n" +       // bbox (s,w) -> (n,e)\n"
                        "                \"location\" : {\n" +
                        "                    \"top_left\" : {\n" +
                        "                        \"lat\" : %s,\n" + //n
                        "                        \"lon\" : %s\n" +  //w
                        "                    },\n" +
                        "                    \"bottom_right\" : {\n" +
                        "                        \"lat\" : %s,\n" + //s
                        "                        \"lon\" : %s\n" + //e
                        "                    }\n" +
                        "                }\n" +
                        "            }\n" +
                        "        }\n" +
                        "    }\n" +
                        "  }\n" +
                        "}",
                n, w, s, e);
    }

    private Boolean isValidToken(String idStringToken)  {
        WSRequest request = ws.url(getValidateTokenUrl(idStringToken));
        F.Promise<WSResponse> responsePromise = request.get();
        return responsePromise.map(response -> response.getStatus() == 200).get(3000);
    }

    private String getSearchUrl() {
        return config.getString("url.search");
    }

    private String getAddPointUrl() {
        return config.getString("url.addPoint");
    }

    private String getValidateTokenUrl(String id_token) {
        return config.getString("url.validateToken")+id_token;
    }


}
