package com.example.systemsecurite;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;


public class HistoryManager {

    private static final String PREFS_NAME = "HistoriquePrefs";
    private static final String KEY_EVENTS = "events";
    private static final int MAX_ENTRIES = 200;

    public static class HistoryEntry {
        public final String label;
        public final String dateTime;

        public HistoryEntry(String label, String dateTime) {
            this.label = label;
            this.dateTime = dateTime;
        }
    }

    public static void addEntry(Context context, String label, String dateTime) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        try {
            JSONArray oldArray = new JSONArray(prefs.getString(KEY_EVENTS, "[]"));
            JSONArray newArray = new JSONArray();

            JSONObject obj = new JSONObject();
            obj.put("label", label);
            obj.put("dateTime", dateTime);
            newArray.put(obj);

            int limit = Math.min(oldArray.length(), MAX_ENTRIES - 1);
            for (int i = 0; i < limit; i++) {
                newArray.put(oldArray.get(i));
            }

            prefs.edit().putString(KEY_EVENTS, newArray.toString()).apply();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public static List<HistoryEntry> getEntries(Context context) {
        List<HistoryEntry> list = new ArrayList<>();
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        try {
            JSONArray array = new JSONArray(prefs.getString(KEY_EVENTS, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                list.add(new HistoryEntry(obj.getString("label"), obj.getString("dateTime")));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return list;
    }

    public static void clearHistory(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(KEY_EVENTS).apply();
    }
}