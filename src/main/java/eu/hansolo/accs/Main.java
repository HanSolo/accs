/*
 * Copyright (c) 2016 by Gerrit Grunwald
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.hansolo.accs;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import spark.Spark;

import java.util.Optional;


/**
 * Created by hansolo on 15.06.16.
 */
public class Main {
    private static final Optional<String> PORT                = Optional.ofNullable(System.getenv("PORT"));
    private enum UMLAUT {
        Ae("\u00C4", "Ae"),
        Ue("\u00DC", "Ue"),
        Oe("\u00D6", "Oe"),
        ae("\u00E4", "ae"),
        ue("\u00FC", "ue"),
        oe("\u00F6", "oe"),
        sz("\u00DF", "ss");

        public final String ORIGINAL;
        public final String REPLACEMENT;

        UMLAUT(final String ORIGINAL, final String REPLACEMENT) {
            this.ORIGINAL    = ORIGINAL;
            this.REPLACEMENT = REPLACEMENT;
        }

        public static String replaceUmlauts(final String WORD) {
            String tmp = WORD;
            for(UMLAUT umlaut : values()) { tmp = tmp.replaceAll(umlaut.ORIGINAL, umlaut.REPLACEMENT); }
            return tmp;
        }
    }


    // ******************** Constructors **************************************
    public Main() {
        // Abort if no PORT variable set
        if (!PORT.isPresent()) System.exit(0);

        // Port
        Spark.port(Integer.parseInt(PORT.get()));

        // CORS filter
        Spark.before((request, response) -> {
            response.header("Access-Control-Allow-Origin", request.headers("origin"));
            response.header("Access-Control-Allow-Headers", "Origin, x-requested-with, content-type, Accept");
            response.header("Access-Control-Request-Method", "GET,PUT,POST,DELETE,OPTIONS");
        });


        // REST GET endpoints
        Spark.get("/", (request, response) -> {
            response.type("application/json");
            return new JSONObject().toJSONString();
        });
        Spark.get("/locations", (request, response) -> {
            response.type("application/json");
            return RestClient.INSTANCE.getAllLocations().toJSONString();
        });
        Spark.get("/location", (request, response) -> {
            response.type("application/json");
            return RestClient.INSTANCE.getLocation(request.queryParams("name"));
        });


        // REST POST endpoints
        Spark.post("/add", (request, response) -> {
            response.status(200);
            if (request.contentType().equals("application/json") ||
                request.contentType().equals("application/json; charset=utf-8") ||
                request.contentType().equals("text/plain")) {
                Object obj = JSONValue.parse(request.body());
                return addLocation(new Location((JSONObject) obj));
            }
            return String.join(" ", "{", "}");
        });

        Spark.put("/update", (request, response) -> {
            response.status(200);
            if (request.contentType().equals("application/json") ||
                request.contentType().equals("application/json; charset=utf-8") ||
                request.contentType().equals("text/plain")) {
                Object obj = JSONValue.parse(request.body());
                return updateLocation(new Location((JSONObject) obj));
            }
            return String.join(" ", "{", "}");
        });

        Cleaner.INSTANCE.start();
    }


    // ******************** Methods *******************************************
    private JSONObject addLocation(final Location LOCATION) {
        LOCATION.info = updateInfo(LOCATION);
        RestClient.INSTANCE.postLocation(LOCATION);
        return LOCATION.toJSON();
    }

    private JSONObject updateLocation(final Location LOCATION) {
        //LOCATION.info = updateInfo(LOCATION);
        RestClient.INSTANCE.putLocation(updateLocationInfo(LOCATION));
        return LOCATION.toJSON();
    }

    private Location updateLocationInfo(final Location LOCATION) {
        JSONObject   json   = RestClient.INSTANCE.getAddress(LOCATION.latitude, LOCATION.longitude);
        final String STATUS = json.get("status").toString();
        if (STATUS.equals("OK")) {
            JSONArray  results           = (JSONArray) json.get("results");
            JSONObject addressComponents = (JSONObject) results.get(0);
            String[]   formattedAddress  = addressComponents.get("formatted_address").toString().split(",");
            int        length            = formattedAddress.length;
            if (length > 2) {
                String city    = UMLAUT.replaceUmlauts(formattedAddress[length - 2].trim().replaceAll("\\P{L}+", ""));
                String country = formattedAddress[length - 1].trim();
                LOCATION.info  = String.join("", city, ", ", country);
                return LOCATION;
            }
        } else {
            return LOCATION;
        }
        return LOCATION;
    }

    private String updateInfo(final Location LOCATION) {
        if (LOCATION.isZero()) return "";

        JSONObject   json   = RestClient.INSTANCE.getAddress(LOCATION.latitude, LOCATION.longitude);
        final String STATUS = json.get("status").toString();
        if (STATUS.equals("OK")) {
            JSONArray  results           = (JSONArray) json.get("results");
            JSONObject addressComponents = (JSONObject) results.get(0);
            String[]   formattedAddress  = addressComponents.get("formatted_address").toString().split(",");
            int        length            = formattedAddress.length;
            if (length > 2) {
                String city    = UMLAUT.replaceUmlauts(formattedAddress[length - 2].trim().replaceAll("\\P{L}+", ""));
                String country = formattedAddress[length - 1].trim();
                return String.join("", city, ", ", country);
            }
        } else {
            return "";
        }
        return "";
    }

    public static void main(String[] args) { new Main(); }
}
