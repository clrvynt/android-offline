package com.kk.noteservice.util;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class GsonDateAdapter extends TypeAdapter<Date> {
    private static final String FORMAT = "yyyy-MM-dd'T'HH:mm:ssZ";
    @Override
    public void write(JsonWriter writer, Date value) throws IOException {
        if (value == null) {
            writer.nullValue();
            return;
        }
        SimpleDateFormat sdf = new SimpleDateFormat(FORMAT, Locale.US);
        writer.value(sdf.format(value));
    }

    @Override
    public Date read(JsonReader reader) throws IOException {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull();
            return null;
        }
        String dateString = reader.nextString();
        SimpleDateFormat sdf = new SimpleDateFormat(FORMAT, Locale.US);
        try {
            return sdf.parse(dateString);
        } catch (ParseException e) {
            return null;
        }
    }
}
