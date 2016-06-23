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

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


/**
 * Created by hansolo on 15.06.16.
 */
public enum RestClient {
    INSTANCE;

    private static final Optional<String> LOCATION_URL = Optional.ofNullable(System.getenv("LOCATION_URL"));
    private static final Optional<String> API_KEY      = Optional.ofNullable(System.getenv("MLAB_API_KEY"));

    public enum DbCollection {
        LOCATIONS(LOCATION_URL.isPresent() ? LOCATION_URL.get() : "");

        public final String REST_URL;

        DbCollection(final String REST_URL) {
            this.REST_URL = REST_URL;

            // Abort if REST_URL is empty
            if (REST_URL.isEmpty()) System.exit(0);
        }
    }

    private static final   String MLAB_API_KEY = API_KEY.isPresent() ? API_KEY.get() : "";
    private HttpClient     httpClient;
    private List<Location> locationList;


    // ******************** Constructors **************************************
    RestClient() {
        httpClient   = HttpClientBuilder.create().build();
        locationList = new ArrayList<>(64);
    }


    // ******************** Public Methods ************************************
    public JSONArray getAllLocations() { return getAll(DbCollection.LOCATIONS); }
    public List<Location> getAllLocationsAsList() {
        updateLocations();
        return locationList;
    }
    public JSONObject getLocation(final String NAME) {
        URIBuilder builder = new URIBuilder();
        builder.setScheme("https")
               .setHost("api.mlab.com")
               .setPort(443)
               .setPath(DbCollection.LOCATIONS.REST_URL)
               .setParameter("q", "{\"name\":\"" + NAME + "\"}")
               .setParameter("apiKey", MLAB_API_KEY);
        return getSpecificObject(builder);
    }
    public JSONObject getLocation(final double LATITUDE, final double LONGITUDE) {
        URIBuilder builder = new URIBuilder();
        builder.setScheme("https")
               .setHost("api.mlab.com")
               .setPort(443)
               .setPath(DbCollection.LOCATIONS.REST_URL)
               .setParameter("q", "{" +
                                  "\"latitude\":\"" + LATITUDE + "\"," +
                                  "\"longitude\":\"" + LONGITUDE + "\"}")
               .setParameter("apiKey", MLAB_API_KEY);
        return getSpecificObject(builder);
    }
    public void postLocation(final Location LOCATION) {
        URIBuilder builder = new URIBuilder();
        builder.setScheme("https")
               .setHost("api.mlab.com")
               .setPort(443)
               .setPath(DbCollection.LOCATIONS.REST_URL)
               .setParameter("apiKey", MLAB_API_KEY);
        postSpecific(builder, LOCATION);
    }
    public void putLocation(final Location LOCATION) {
        JSONObject jsonObject = getLocation(LOCATION.name);
        final String OID = ((JSONObject) JSONValue.parse(jsonObject.get("_id").toString())).get("$oid").toString();
        URIBuilder builder = new URIBuilder();
        builder.setScheme("https")
               .setHost("api.mlab.com")
               .setPort(443)
               .setPath(String.join("/", DbCollection.LOCATIONS.REST_URL, OID))
               //.setParameter("u", "true")
               .setParameter("apiKey", MLAB_API_KEY);
        putSpecific(builder, LOCATION);
        updateLocations();
    }
    public void deleteLocation(final Location LOCATION) {
        JSONObject jsonObject = getLocation(LOCATION.name);
        final String OID = ((JSONObject) JSONValue.parse(jsonObject.get("_id").toString())).get("$oid").toString();
        URIBuilder builder = new URIBuilder();
        builder.setScheme("https")
               .setHost("api.mlab.com")
               .setPort(443)
               .setPath(String.join("/", DbCollection.LOCATIONS.REST_URL, OID))
               .setParameter("apiKey", MLAB_API_KEY);
        deleteSpecific(builder);
    }

    public JSONObject getAddress(final double LATITUDE, final double LONGITUDE) {
        try {
            HttpGet get = new HttpGet("http://maps.google.com/maps/api/geocode/json?latlng=" + LATITUDE + "," + LONGITUDE + "&sensor=false");
            get.addHeader("accept", "application/json");

            HttpResponse response = httpClient.execute(get);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                //throw new RuntimeException("Failed: HTTP error code: " + statusCode);
                return new JSONObject();
            }

            StringBuilder       output = new StringBuilder();
            try (BufferedReader br     = new BufferedReader(new InputStreamReader(response.getEntity().getContent()))) {
                String line;
                while ((line = br.readLine()) != null) { output.append(line); }
            } catch (IOException exception) {
                System.out.println("Error: " + exception);
            }
            JSONObject jsonObject = (JSONObject) JSONValue.parse(output.toString());
            return jsonObject;
        } catch (IOException exception) {
            return new JSONObject();
        }
    }


    // ******************** Private Methods ***********************************
    private void updateLocations() {
        locationList.clear();
        JSONArray locationsArray = getAllLocations();
        for (int i = 0 ; i < locationsArray.size() ; i++) {
            JSONObject jsonLocation = (JSONObject) locationsArray.get(i);
            locationList.add(new Location(jsonLocation));
        }
    }


    private JSONArray getAll(final DbCollection COLLECTION) {
        try {
            URIBuilder builder = new URIBuilder();
            builder.setScheme("http")
                   .setHost("api.mlab.com")
                   .setPort(80)
                   .setPath(COLLECTION.REST_URL)
                   .setParameter("apiKey", MLAB_API_KEY);
            HttpGet get = new HttpGet(builder.build());
            get.setHeader("accept", "application/json");

            HttpResponse response = httpClient.execute(get);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                //throw new RuntimeException("Failed: HTTP error code: " + statusCode);
                return new JSONArray();
            }

            StringBuilder output = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(response.getEntity().getContent()))) {
                String line;
                while ((line = br.readLine()) != null) { output.append(line); }
            } catch (IOException exception) {
                System.out.println("Error: " + exception);
            }
            JSONArray jsonArray = (JSONArray) JSONValue.parse(output.toString());
            return jsonArray;
        } catch (IOException | URISyntaxException exception) {
            System.out.println("Error: " + exception);
            return new JSONArray();
        }
    }

    private JSONObject getSpecificObject(final URIBuilder BUILDER) {
        try {
            HttpGet get = new HttpGet(BUILDER.build());

            HttpResponse response = httpClient.execute(get);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                //throw new RuntimeException("Failed: HTTP error code: " + statusCode);
                return new JSONObject();
            }

            StringBuilder       output = new StringBuilder();
            try (BufferedReader br     = new BufferedReader(new InputStreamReader(response.getEntity().getContent()))) {
                String line;
                while ((line = br.readLine()) != null) { output.append(line); }
            } catch(IOException exception) {
                System.out.println("Error: " + exception);
            }
            JSONArray  jsonArray  = (JSONArray) JSONValue.parse(output.toString());
            JSONObject jsonObject = jsonArray.size() > 0 ? (JSONObject) jsonArray.get(0) : new JSONObject();
            return jsonObject;
        } catch (IOException | URISyntaxException exception) {
            System.out.println("Error: " + exception);
            return new JSONObject();
        }
    }
    private JSONArray getSpecificArray(final URIBuilder BUILDER) {
        try {
            HttpGet get = new HttpGet(BUILDER.build());

            HttpResponse response = httpClient.execute(get);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                //throw new RuntimeException("Failed: HTTP error code: " + statusCode);
                return new JSONArray();
            }

            StringBuilder       output = new StringBuilder();
            try (BufferedReader br     = new BufferedReader(new InputStreamReader(response.getEntity().getContent()))) {
                String line;
                while ((line = br.readLine()) != null) { output.append(line); }
            } catch(IOException exception) {
                System.out.println("Error: " + exception);
            }
            JSONArray jsonArray = (JSONArray) JSONValue.parse(output.toString());
            return jsonArray;
        } catch (IOException | URISyntaxException exception) {
            System.out.println("Error: " + exception);
            return new JSONArray();
        }
    }
    private JSONObject postSpecific(final URIBuilder BUILDER, final Location LOCATION) {
        try {
            HttpPost post = new HttpPost(BUILDER.build());
            post.setHeader("Content-type", "application/json");
            post.setHeader("accept", "application/json");
            post.setEntity(new StringEntity(LOCATION.toJSONString()));

            return handleResponse(httpClient.execute(post));
        } catch (IOException | URISyntaxException exception) {
            System.out.println("Error: " + exception);
            return new JSONObject();
        }
    }
    private JSONObject putSpecific(final URIBuilder BUILDER, final Location LOCATION) {
        try {
            HttpPut put = new HttpPut(BUILDER.build());
            put.setHeader("Content-type", "application/json");
            put.setHeader("accept", "application/json");
            put.setEntity(new StringEntity(LOCATION.toJSONString()));

            return handleResponse(httpClient.execute(put));
        } catch (IOException | URISyntaxException exception) {
            System.out.println("Error: " + exception);
            return new JSONObject();
        }
    }
    private JSONObject deleteSpecific(final URIBuilder BUILDER) {
        try {
            HttpDelete delete = new HttpDelete(BUILDER.build());
            delete.setHeader("Content-type", "application/json");
            delete.setHeader("accept", "application/json");

            return handleResponse(httpClient.execute(delete));
        } catch (IOException | URISyntaxException exception) {
            System.out.println("Error: " + exception);
            return new JSONObject();
        }
    }

    private JSONObject handleResponse(final HttpResponse RESPONSE) {
        int statusCode = RESPONSE.getStatusLine().getStatusCode();
        if (statusCode != 200) {
            //throw new RuntimeException("Failed: HTTP error code: " + statusCode);
            return new JSONObject();
        }

        StringBuilder       output = new StringBuilder();
        try (BufferedReader br     = new BufferedReader(new InputStreamReader(RESPONSE.getEntity().getContent()))) {
            String line;
            while ((line = br.readLine()) != null) { output.append(line); }
        } catch(IOException exception) {
            System.out.println("Error: " + exception);
        }
        JSONObject jsonObject = (JSONObject) JSONValue.parse(output.toString());
        return jsonObject;
    }
}
