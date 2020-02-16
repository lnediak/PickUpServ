package com.example.pickupserv;

import org.json.*;

import java.awt.*;
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

    public static void runInBackground() {
        (new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Server.listen();
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        })).run();
    }

    private Socket client;

    private static class EventStats {

        public String host;
        public int currSize;
        public String[] members;
        public String desc;
        public RadarIO.Location loc;

        EventStats(String host, int capacity, String desc, RadarIO.Location loc) {
            this.host = host;
            members = new String[capacity];
            currSize = 0;
            this.desc = desc;
            this.loc = loc;
        }

    }

    private static ConcurrentHashMap<String, EventStats> events = new ConcurrentHashMap<String, EventStats>();
    private static ConcurrentHashMap<String, String> hostToEvent = new ConcurrentHashMap<String, String>();
    private static ConcurrentHashMap<String, String> userToEvent = new ConcurrentHashMap<String, String>();

    private static long lastRefresh;
    private static ConcurrentHashMap<String, Double> activeUsers = new ConcurrentHashMap<String, Double>();

    private static class UserStats {

        public RadarIO.Location loc;
        public String username;

        UserStats(RadarIO.Location loc, String username) {
            this.loc = loc;
            this.username = username;
        }

    }

    private static ConcurrentHashMap<String, UserStats> users = new ConcurrentHashMap<String, UserStats>();

    private String readLine(InputStream is) throws IOException {
        int c;
        StringBuilder sb = new StringBuilder();
        while ((c = is.read()) != -1 && c != '\n') {
            sb.append((char)c);
        }
        if (c == -1 && sb.length() == 0) {
            return null;
        }
        return sb.toString();
    }

    Server(Socket client) {
        this.client = client;
    }

    private final long POLL_DELAY = 1000;

    private synchronized void refreshUserInfo() throws Throwable {
        long currTime = System.currentTimeMillis();
        if (currTime - lastRefresh >= POLL_DELAY) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX");
            Date last = new Date(lastRefresh);
            lastRefresh = currTime;
            String updatedAfter = sdf.format(last);
            JSONArray usersl = RadarIO.listUsers(updatedAfter);
            for (Object user : usersl) {
                JSONObject obj = (JSONObject)user;
                String devId = obj.get("deviceId").toString();
                users.put(devId, new UserStats(new RadarIO.Location(obj.getJSONObject("location")), users.containsKey(devId) ? users.get(devId).username : devId));
                if (activeUsers.containsKey(devId)) {
                    String geoid = hostToEvent.containsKey(devId) ? hostToEvent.get(devId) : userToEvent.get(devId);
                    JSONArray fences = obj.getJSONArray("geofences");
                    boolean notIn = true;
                    for (Object fence : fences) {
                        JSONObject fobj = (JSONObject)fence;
                        if (fobj.get("_id").toString().equals(geoid)) {
                            activeUsers.put(devId, new Double(0));
                            notIn = false;
                            break;
                        }
                    }
                    if (notIn) {
                        double dist = RadarIO.getDistance(users.get(devId).loc, events.get(geoid).loc);
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
            InputStream is = client.getInputStream();
            OutputStream os = client.getOutputStream();
            String devId = readLine(is);
            if (devId == null) {
                os.write("Did not expect EOF".getBytes());
                return;
            }
            if (!users.containsKey(devId)) {
                try {
                    Thread.sleep(POLL_DELAY + 10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            refreshUserInfo();
            if (!users.containsKey(devId)) {
                os.write("User not found".getBytes());
                System.out.println("Poop");
                return;
            }
            int b = is.read();
            String str = readLine(is);
            int clen;
            try {
                clen = Integer.parseInt(str);
            } catch (NumberFormatException e) {
                os.write("Content-length is not integer".getBytes());
                return;
            }
            String[] fullIn = new String[0];
            if (clen > 0) {
                byte[] buf = new byte[clen];
                int off = 0;
                int cou;
                while (off + (cou = is.read(buf, off, clen - off)) < clen) {
                    off += cou;
                }
                fullIn = (new String(buf)).split("\n");
            }
            switch (b) {
            case 'c':
                createGeofence(devId, fullIn, os);
                break;
            case 'd':
                if (!hostToEvent.containsKey(devId)) {
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
                os.write(255);
                break;
            case 'u':
                updateParticipantInfo(devId, fullIn, os);
                break;
            case 's':
                searchEvents(devId, fullIn, os);
                break;
            case 'j':
                joinEvent(devId, fullIn, os);
                break;
            case 'l':
                leaveEvent(devId, fullIn, os);
                break;
            case 'n':
                nameSet(devId, fullIn, os);
                break;
            default:
                os.write(("Unsupported request" + (char)b).getBytes());
            }
        } catch (Throwable e) {
            try {
                OutputStream os = client.getOutputStream();
                os.write(e.toString().getBytes());
            } catch (Throwable ee) {
                ee.printStackTrace();
            }
        } finally {
            try {
                byte[] buf = new byte[8192];
                client.getOutputStream().flush();
                client.shutdownOutput();
                InputStream is = client.getInputStream();
                while (is.read(buf) != -1) {}
                client.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void createGeofence(String devId, String[] fullIn, OutputStream os) throws Throwable {
        if (fullIn.length < 3) {
            os.write("Did not expect EOF".getBytes());
            return;
        }
        String radiuss = fullIn[0];
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
        String cap = fullIn[1];
        int capacity;
        try {
            capacity = Integer.parseInt(cap);
        } catch (NumberFormatException e) {
            os.write("Capacity not an integer".getBytes());
            return;
        }
        if (capacity < 2) {
            os.write("Capacity out of range".getBytes());
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 2; i < fullIn.length; i++) {
            sb.append(fullIn[i]);
        }
        String desc = sb.toString();
        RadarIO.Location loc = users.get(devId).loc;
        synchronized (events) {
            if (hostToEvent.containsKey(devId)) {
                os.write("Error: Host is already hosting an event".getBytes());
                return;
            }
            if (userToEvent.containsKey(devId)) {
                os.write("Error: Host is already participating in an event".getBytes());
                return;
            }
            String geoid = RadarIO.createGeofence(desc, loc, radius);
            events.put(geoid, new EventStats(devId, capacity, desc, loc));
            hostToEvent.put(devId, geoid);
            activeUsers.put(devId, new Double(-1));
        }
        os.write(255);
    }

    private void writeUserStats(EventStats att, OutputStream os) throws Throwable {
        os.write((users.get(att.host).username + "\n").getBytes());
        os.write(("" + activeUsers.get(att.host) + "\n").getBytes());
        for (int i = 0; i < att.currSize; i++) {
            os.write((users.get(att.members[i]).username + "\n").getBytes());
            os.write(("" + activeUsers.get(att.host) + "\n").getBytes());
        }
    }

    private void updateParticipantInfo(String devId, String[] fullIn, OutputStream os) throws Throwable {
        os.write((users.get(devId).loc.latlong + "\n").getBytes());
        if (!hostToEvent.containsKey(devId) && !userToEvent.containsKey(devId)) {
            return;
        }
        String geoid = hostToEvent.containsKey(devId) ? hostToEvent.get(devId) : userToEvent.get(devId);
        EventStats att = events.get(geoid);
        writeUserStats(att, os);
        os.write(255);
    }

    private void searchEvents(String devId, String[] fullIn, OutputStream os) throws Throwable {
        if (fullIn.length < 1) {
            os.write("Did not expect EOF".getBytes());
            return;
        }
        String radS = fullIn[0];
        int radius;
        try {
            radius = Integer.parseInt(radS);
        } catch (NumberFormatException e) {
            os.write("Radius not an integer".getBytes());
            return;
        }
        if (radius < 100 || radius > 10000) {
            os.write("Radius out of range".getBytes());
            return;
        }
        ArrayList<String> results = RadarIO.searchGeofences(1000, users.get(devId).loc, radius);
        for (String geoid : results) {
            os.write((geoid + "\n").getBytes());
            EventStats att = events.get(geoid);
            os.write((att.loc.latlong + "\n").getBytes());
            os.write(att.desc.getBytes());
            os.write(254);
            os.write('\n');
            writeUserStats(att, os);
            os.write(254);
            os.write('\n');
        }
        os.write(255);
    }

    private synchronized void joinEvent(String devId, String[] fullIn, OutputStream os) throws Throwable {
        if (fullIn.length < 1) {
            os.write("Did not expect EOF".getBytes());
            return;
        }
        String eventID = fullIn[0];
        if (!events.containsKey(eventID)) {
            os.write("Event does not exist".getBytes());
            return;
        }
        if (hostToEvent.containsKey(devId)) {
            os.write("Error: Host is already hosting an event".getBytes());
            return;
        }
        if (userToEvent.containsKey(devId)) {
            os.write("Error: Host is already participating in an event".getBytes());
            return;
        }
        if (events.get(eventID).members.length == events.get(eventID).currSize) {
            os.write("Event capacity full".getBytes());
            return;
        }
        userToEvent.put(devId, eventID);
        EventStats eventToJoin = events.get(eventID);
        eventToJoin.members[eventToJoin.currSize++] = eventID;
        activeUsers.put(devId, new Double(-1));
        os.write(255);
    }

    private synchronized void leaveEvent(String devId, String[] fullIn, OutputStream os) throws Throwable {
        if (fullIn.length < 1) {
            os.write("Did not expect EOF".getBytes());
            return;
        }
        String eventID = fullIn[0];
        if (!events.containsKey(eventID)) {
            os.write("Event does not exist".getBytes());
            return;
        }
        if (hostToEvent.containsKey(devId)) {
            os.write("Host must disband event to leave".getBytes());
            return;
        }
        if (!userToEvent.containsKey(devId)) {
            os.write("User is not in any event".getBytes());
            return;
        }
        if (!userToEvent.get(devId).equals(eventID)) {
            os.write("User is not in specified event".getBytes());
            return;
        }
        EventStats eventToLeave = events.get(eventID);
        //Delete devID from eventToLeave.members
        int i = 0;
        while (i < eventToLeave.currSize && eventToLeave.members[i] != devId) {
            i++;
        }
        for (i++; i < eventToLeave.currSize; i++) {
            eventToLeave.members[i - 1] = eventToLeave.members[i];
        }
        eventToLeave.currSize--;
        userToEvent.remove(devId);
        activeUsers.remove(devId);
        os.write(255);
    }

    private void nameSet(String devId, String[] fullIn, OutputStream os) throws Throwable {
        if (fullIn.length < 1) {
            os.write("Did not expect EOF".getBytes());
            return;
        }
        String name = fullIn[0];
        if (name == null) {
            os.write("Did not expect EOF".getBytes());
            return;
        }
        if (name.contains("\n") || name.contains("\r")) {
            os.write("Name cannot use newline characters".getBytes());
            return;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == 254 || c == 255) {
                os.write("Invalid characters in username".getBytes());
                return;
            }
        }
        synchronized (events) {
            users.get(devId).username = name;
        }
        os.write(255);
    }

}
