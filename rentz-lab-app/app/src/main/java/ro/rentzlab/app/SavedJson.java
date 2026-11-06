package ro.rentzlab.app;

import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** One strict token reader on JVM and Android; org.json only stores already validated values. */
final class SavedJson {
    private static final Pattern INTEGER = Pattern.compile("-?(0|[1-9][0-9]*)");
    private static final int MAX_DEPTH = 16;

    private SavedJson() { }

    static JSONObject read(String text) throws JSONException {
        if (text == null) throw new JSONException("Saved JSON is missing");
        try (var reader = new JsonReader(new StringReader(text))) {
            reader.setStrictness(Strictness.STRICT);
            if (reader.peek() != JsonToken.BEGIN_OBJECT) throw new JSONException("Saved object required");
            var object = object(reader, 0);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new JSONException("Trailing saved content");
            return object;
        } catch (IOException | NumberFormatException error) {
            var failure = new JSONException("Invalid saved JSON: " + error.getMessage());
            failure.initCause(error);
            throw failure;
        }
    }

    private static JSONObject object(JsonReader reader, int depth) throws IOException, JSONException {
        var result = new JSONObject();
        var names = new HashSet<String>();
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (!names.add(name)) throw new JSONException("Duplicate saved property: " + name);
            result.put(name, value(reader, depth + 1));
        }
        reader.endObject();
        return result;
    }

    private static Object value(JsonReader reader, int depth) throws IOException, JSONException {
        if (depth > MAX_DEPTH) throw new JSONException("Saved JSON nesting limit");
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> object(reader, depth);
            case BEGIN_ARRAY -> {
                var values = new JSONArray();
                reader.beginArray();
                while (reader.hasNext()) values.put(value(reader, depth + 1));
                reader.endArray();
                yield values;
            }
            case STRING -> reader.nextString();
            case BOOLEAN -> reader.nextBoolean();
            case NULL -> { reader.nextNull(); yield JSONObject.NULL; }
            case NUMBER -> {
                String token = reader.nextString();
                if (!INTEGER.matcher(token).matches()) throw new JSONException("Saved integer token required");
                yield Long.parseLong(token);
            }
            default -> throw new JSONException("Unexpected saved token");
        };
    }

    static void fields(JSONObject object, Set<String> required, Set<String> optional) throws JSONException {
        for (String name : required) if (!object.has(name)) throw new JSONException("Missing saved field: " + name);
        var names = object.keys();
        while (names.hasNext()) {
            String name = names.next();
            if (!required.contains(name) && !optional.contains(name)) throw new JSONException("Unknown saved field: " + name);
        }
    }

    static long integer(JSONObject object, String name) throws JSONException {
        Object value = object.get(name);
        if (value instanceof Long || value instanceof Integer) return ((Number) value).longValue();
        throw new JSONException("Saved integer required: " + name);
    }

    static int intValue(JSONObject object, String name) throws JSONException {
        long value = integer(object, name);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) throw new JSONException("Saved integer out of range: " + name);
        return (int) value;
    }

    static String text(JSONObject object, String name) throws JSONException {
        Object value = object.get(name);
        if (value instanceof String) return (String) value;
        throw new JSONException("Saved text required: " + name);
    }

    static boolean bool(JSONObject object, String name) throws JSONException {
        Object value = object.get(name);
        if (value instanceof Boolean) return (Boolean) value;
        throw new JSONException("Saved boolean required: " + name);
    }
}
