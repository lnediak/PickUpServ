package com.example.pickupserv;

import org.json.*;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import javax.net.ssl.*;

public class RadarIO {

    public static class Location {

        public String loc;
        public String latlong;

        Location(JSONObject obj) {
            loc = obj.getString("coordinates");
            String temp = loc.substring(1, loc.length() - 1);
            String[] arr = loc.split(",");
            latlong = arr[1] + "," + arr[0];
        }

    }

    private static JSONObject sendRequest(String url, String type, String data) throws MalformedURLException, SocketTimeoutException, JSONException, IOException {
        URL uurl = new URL(url);
        if (uurl.getProtocol() != "https") {
          throw new MalformedURLException("URL not HTTPS");
        }
        HttpsURLConnection conn = (HttpsURLConnection) uurl.openConnection();
        conn.setRequestMethod(type);
        conn.setRequestProperty("Authorization", "INSERT PRIVATE KEY HERE");
        conn.setDoInput(true);
        if (type == "POST") {
          conn.setRequestProperty("Content-Length", "" + data.getBytes().length);
          conn.setUseCaches(false);
          conn.setDoOutput(true);
          conn.getOutputStream().write(data.getBytes());
        }
        JSONObject toreturn = new JSONObject(new JSONTokener(conn.getInputStream()));
        return toreturn;
    }

    /**
     * Create a new geofence with desc and
     * @param desc
     * @param loc
     * @param radius
     * @return
     * @throws MalformedURLException
     * @throws SocketTimeoutException
     * @throws JSONException
     * @throws IOException
     */
    public static String createGeofence(String desc, Location loc, double radius) throws MalformedURLException,
            SocketTimeoutException, JSONException, IOException {
        JSONObject obj = sendRequest("https://api.radar.io/v1/geofences", "POST",
            "description=" + desc
         + "&type=circle"
         + "&coordinates=" + loc.loc
         + "&radius=" + radius);
        String code = obj.getJSONObject("meta").getString("code");
        if (!code.startsWith("2")) {
            throw new IOException("Radar.io responded with Non-OK response code " + code);
        }
        return obj.getJSONObject("geofence").getString("_id");
    }

    /**
     * Delete an existing geofence.
     * @param id
     * @param loc
     * @param radius
     * @throws MalformedURLException
     * @throws SocketTimeoutException
     * @throws JSONException
     * @throws IOException
     */
    public static void deleteGeofence(String id) throws MalformedURLException,
            SocketTimeoutException, JSONException, IOException {
        JSONObject obj = sendRequest("https://api.radar.io/v1/geofences/" + id, "DELETE", "");
        String code = obj.getJSONObject("meta").getString("code");
        if (!code.startsWith("2")) {
            throw new IOException("Radar.io responded with Non-OK response code " + code);
        }
    }

    public static JSONObject getUser(String deviceId) throws MalformedURLException,
            SocketTimeoutException, JSONException, IOException {
        JSONObject obj = sendRequest("https://api.radar.io/v1/users/" + deviceId, "GET", "");
        String code = obj.getJSONObject("meta").getString("code");
        if (!code.startsWith("2")) {
            throw new IOException("Radar.io responded with Non-OK response code " + code);
        }
        return obj;

    }

    public static ArrayList<String> searchGeofences(int limit, Location currLoc, int radius) throws Throwable {
        JSONObject obj = sendRequest("https://api.radar.io/v1/search/geofences?near=" +
                  currLoc.latlong + "&radius=" + radius +
                  "&limit=" + limit, "GET", "");
        String code = obj.getJSONObject("meta").getString("code");
        if (!code.startsWith("2")) {
            throw new IOException("Radar.io responded with Non-OK response code " + code);
        }
        ArrayList<String> temp = new ArrayList<String>();
        for(Object geofenceInfo : obj.getJSONArray("geofences")) {
            temp.add(((JSONObject)geofenceInfo).getString("_id"));
        }
        return temp;
    }

    public static JSONArray listUsers(String updatedBefore, String updatedAfter) throws Throwable {
        JSONObject obj = sendRequest("https://api.radar.io/v1/users?limit=1000" +
                "&updatedBefore=" + updatedBefore + "&updatedAfter=" + updatedAfter, "GET", "");
        String code = obj.getJSONObject("meta").getString("code");
        if (!code.startsWith("2")) {
            throw new IOException("Radar.io responded with Non-OK response code " + code);
        }
        return obj.getJSONArray("users");
    }

    public static double getDistance(Location origin, Location destination) throws Throwable {
        JSONObject obj = sendRequest("https://api.radar.io/v1/route/distance?origin=" + origin.latlong
                               + "&destination=" + destination.latlong + "&units=metric" + "&modes=foot", "GET", "");
        String code = obj.getJSONObject("meta").getString("code");
        if (!code.startsWith("2")) {
            throw new IOException("Radar.io responded with Non-OK response code " + code);
        }
        return Double.parseDouble(obj.getJSONObject("foot").getJSONObject("distance").getString("value"));
    }
    

}
