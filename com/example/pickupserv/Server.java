package com.example.pickupserv;

import java.io.*;
import java.net.*;
import java.nio.channels.IllegalBlockingModeException;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;

public class Server implements Runnable {

    public static void listen() throws SecurityException, SocketTimeoutException, IllegalBlockingModeException, IOException {
        ServerSocket serv = new ServerSocket(12345);
        while (!serv.isClosed()) {
            (new Thread(new Server(serv.accept()))).run();
        }
    }

    private Socket client;

    private static class EventStats {

        public String host;
        public int currSize;
        public String[] members;
        public RadarIO.Location loc;

        EventStats(String host, int capacity, RadarIO.Location loc) {
            this.host = host;
            members = new String[capacity];
            currSize = 0;
            this.loc = loc;
        }

    }

    private static ConcurrentHashMap<String, EventStats> events;
    private static ConcurrentHashMap<String, String> hostToEvent;
    private static ConcurrentHashMap<String, String> userToEvent;

    private static long lastRefresh;
    private static ConcurrentHashMap<String, Double> activeUsers;

    private static class UserStats {

        public RadarIO.Location loc;
        public String username;

        UserStats(RadarIO.Location loc, String username) {
            this.loc = loc;
            this.username = username;
        }

    }

    private static ConcurrentHashMap<String, UserStats> users;

    Server(Socket client) {
        this.client = client;
    }

    private static boolean isDig(int b) {
        return ('0' <= b) && (b <= '9');
    }

    private final long POLL_DELAY = 1000;

    private synchronized void refreshUserInfo() throws Throwable {
        long currTime = System.currentTimeMillis();
        if (currTime - lastRefresh >= POLL_DELAY) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX");
            Date last = new Date(lastRefresh);
            lastRefresh = currTime;
            String updatedAfter = sdf.format(last);
            JsonArray usersl = RadarIO.listUsers(updatedAfter);
            for (JsonValue user : usersl) {
                JsonObject obj = (JsonObject)user;
                String devId = obj.getString("deviceId");
                users.put(devId, new UserStats(new RadarIO.Location(obj.getString("location")), users.contains(devId) ? users.get(devId).username : devId));
                if (activeUsers.contains(devId)) {
                    String geoid = hostToEvent.contains(devId) ? hostToEvent.get(devId) : userToEvent.get(devId);
                    JsonArray fences = obj.getJsonArray("geofences");
                    boolean notIn = true;
                    for (JsonValue fence : fences) {
                        JsonObject fobj = (JsonObject)fence;
                        if (fobj.getString("_id").equals(geoid)) {
                            activeUsers.put(devId, new Double(0));
                            notIn = false;
                            break;
                        }
                    }
                    if (notIn) {
                        double dist = RadarIO.dist(users.get(devId), events.get(geoid).loc);
                        activeUsers.put(devId, new Double(dist));
                    }
                }
            }
        }
    }

    @Override
    public void run() {
        // Requests: create_geofence, destroy_geofence, update_participant_info, search_events, join_event, leave_event, name_set
        try {
            BufferedReader is = new BufferedReader(client.getInputStream());
            OutputStream os = client.getOutputStream();
            String devId = is.readLine();
            if (devId == null) {
                os.write("Did not expect EOF".getBytes());
                return;
            }
            if (!users.contains(devId)) {
                try {
                    Thread.sleep(POLL_DELAY + 10);
                } catch (InteruptedException e) {
                    e.printStackTrace();
                }
            }
            refreshUserInfo();
            if (!users.contains(devId)) {
                os.write("User not found".getBytes());
                return;
            }
            int b = is.read();
            switch (b) {
            case 'c':
                createGeofence(devId, is, os);
                break;
            case 'd':
                if (!hostToEvent.contains(devId)) {
                    os.write("Error: Host is not hosting an event".getBytes());
                    return;
                }
                String geoid = hostToEvent.get(devId);
                RadarIO.deleteGeofence(geoid);
                hostToEvent.remove(devId);
                EventStats att = events.get(geoid);
                for (int i = 0; i < att.currSize; i++) {
                    userToEvent.remove(att.members[i]);
                }
                events.remove(geoid);
                activeUsers.remove(devId);
                os.write(new byte[] {255});
                break;
            case 'u':
                updateParticipantInfo(devId, is, os);
                break;
            case 's':
                searchEvents(devId, is, os);
                break;
            case 'j':
                joinEvent(devId, is, os);
                break;
            case 'l':
                leaveEvent(devId, is, os);
                break;
            default:
                os.write(("Unsupported request" + (char)b).getBytes());
            }
        } catch (Throwable e) {
            try {
                OutputStream os = client.getOutputStream();
                os.write(e.toString().getBytes());
            } catch (Throwable e) {
                e.printStackTrace();
            }
        } finally {
            client.close();
        }
    }

    private void createGeofence(String devId, BufferedReader is, OutputStream os) throws Throwable {
        if (hostToEvent.contains(devId)) {
            os.write("Error: Host is already hosting an event".getBytes());
            return;
        }
        if (userToEvent.contains(devId)) {
            os.write("Error: Host is already participating in an event".getBytes());
            return;
        }
        String radiuss = is.readLine();
        int radius;
        try {
            radius = Integer.parseInt(radiuss);
        } catch (NumberFormatException e) {
            os.write("Radius not an integer".getBytes());
            return;
        }
        if (radius < 50 || radius > 500) {
            os.write("Radius out of range".getBytes());
            return;
        }
        String desc = is.readLine();
        if (desc == null) {
            os.write("Did not expect EOF".getBytes());
            return;
        }
        String cap = is.readLine();
        if (cap == null) {
            os.write("Did not expect EOF".getBytes());
            return;
        }
        int capacity;
        try {
            capacity = Integer.parseInt(capacity);
        } catch (NumberFormatException e) {
            os.write("Capacity not an integer".getBytes());
            return;
        }
        if (capacity < 2) {
            os.write("Capacity out of range".getBytes());
            return;
        }
        RadarIO.Location loc = users.get(devId);
        String geoid = RadarIO.createGeofence(desc, loc, radius);
        events.put(geoid, new EventStats(devId, capacity, loc));
        hostToEvent.put(devId, geoid);
        activeUsers.put(devId, new Double(-1));
        os.write(new byte[] {255});
    }

    private void updateParticipantInfo(String devId, BufferedReader is, OutputStream os) throws Throwable {
        if (!hostToEvent.contains(devId) && !userToEvent.contains(devId)) {
            os.write("Error: User is not in an event".getBytes());
        }
        String geoid = hostToEvent.contains(devId) ? hostToEvent.get(devId) : userToEvent.get(devId);
        EventStats att = events.get(geoid);
        os.write((users.get(att.host).username + "\n").getBytes());
        os.write(("" + activeUsers.get(att.host) + "\n").getBytes());
        for (int i = 0; i < att.currSize; i++) {
            os.write((users.get(att.members[i]).username + "\n").getBytes());
            os.write(("" + activeUsers.get(att.host) + "\n").getBytes());
        }
        os.write(new byte[] {255});
    }

    private void searchEvents(String devId, BufferedReader is, OutputStream os) throws Throwable {
        // TODO: WRITE
    }

    private void joinEvent(String devId, BufferedReader is, OutputStream os) throws Throwable {
        // TODO: WRITE
    }

    private void leaveEvent(String devId, BufferedReader is, OutputStream os) throws Throwable {
        // TODO: WRITE
    }

}
