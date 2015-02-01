/**
 * JBoss, Home of Professional Open Source
 * Copyright Red Hat, Inc., and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.aerogear.sync.jsonpatch;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fge.jsonpatch.JsonPatchException;
import com.github.fge.jsonpatch.mergepatch.JsonMergePatch;
import org.jboss.aerogear.sync.jsonpatch.JsonMergePatchEdit.Builder;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;


public final class JsonMapper {

    private static ObjectMapper om = createObjectMapper();

    private static ObjectMapper createObjectMapper() {
        om = new ObjectMapper();
        final SimpleModule module = new SimpleModule("JsonPatch", new Version(1, 0, 0, null, "aerogear", "sync"));
        module.addDeserializer(JsonMergePatchEdit.class, new EditDeserializer());
        module.addSerializer(JsonMergePatchEdit.class, new EditSerializer());
        module.addDeserializer(JsonMergePatchMessage.class, new PatchMessageDeserializer());
        module.addSerializer(JsonMergePatchMessage.class, new PatchMessageSerializer());
        om.registerModule(module);
        return om;
    }

    private JsonMapper() {
    }

    /**
     * Transforms from JSON to the type specified.
     *
     * @param json the json to be transformed.
     * @param type the Java type that the JSON should be transformed to.
     * @param <T> the type the class to convert to
     * @return T an instance of the type populated with data from the json message.
     */
    public static <T> T fromJson(final String json, final Class<T> type) {
        try {
            return om.readValue(json, type);
        } catch (final Exception e) {
            throw new RuntimeException("error trying to parse json [" + json + ']', e);
        }
    }

    /**
     * Transforms from Java object notation to JSON.
     *
     * @param obj the Java object to transform into JSON.
     * @return {@code String} the json representation for the object.
     */
    public static String toJson(final Object obj) {
        try {
            return om.writeValueAsString(obj);
        } catch (final Exception e) {
            throw new RuntimeException("error trying to parse json [" + obj + ']', e);
        }
    }

    public static String toString(final JsonNode jsonNode) {
        try {
            return om.writeValueAsString(jsonNode);
        } catch (final Exception e) {
            throw new RuntimeException("error trying to serialize jsonNode [" + jsonNode + ']', e);
        }
    }

    /**
     * Return a {@link JsonNode} for the passed in JSON string.
     *
     * @param json the string to be parsed.
     * @return JsonNode the JsonNode representing the passed-in JSON string.
     */
    public static JsonNode asJsonNode(final String json) {
        try {
            return om.readTree(json);
        } catch (final IOException e) {
            throw new RuntimeException("error trying to parse json [" + json + ']', e);
        }
    }

    public static ObjectNode newObjectNode() {
        return om.createObjectNode();
    }

    public static ArrayNode newArrayNode() {
        return om.createArrayNode();
    }

    private static class PatchMessageDeserializer extends JsonDeserializer<JsonMergePatchMessage> {

        @Override
        public JsonMergePatchMessage deserialize(final JsonParser jp, final DeserializationContext ctxt) throws IOException {
            final ObjectCodec oc = jp.getCodec();
            final JsonNode node = oc.readTree(jp);
            final String documentId = node.get("id").asText();
            final String clientId = node.get("clientId").asText();
            final JsonNode jsonEdits = node.get("edits");
            final Queue<JsonMergePatchEdit> edits = new ConcurrentLinkedQueue<JsonMergePatchEdit>();
            if (jsonEdits.isArray()) {
                for (JsonNode edit : jsonEdits) {
                    if (edit.isNull()) {
                        continue;
                    }
                    final Builder eb = JsonMergePatchEdit.withDocumentId(documentId).clientId(clientId);
                    eb.clientVersion(edit.get("clientVersion").asLong());
                    eb.serverVersion(edit.get("serverVersion").asLong());
                    eb.checksum(edit.get("checksum").asText());
                    final JsonNode diffsNode = edit.get("diffs");
                    if (!diffsNode.isNull()) {
                        try {
                            eb.diff(JsonMergePatch.fromJson(diffsNode));
                        } catch (final JsonPatchException e) {
                            throw new RuntimeException(e.getMessage(), e);
                        }
                    }
                    edits.add(eb.build());
                }
            }
            return new JsonMergePatchMessage(documentId, clientId, edits);
        }
    }

    private static class PatchMessageSerializer extends JsonSerializer<JsonMergePatchMessage> {

        @Override
        public void serialize(final JsonMergePatchMessage patchMessage,
                              final JsonGenerator jgen,
                              final SerializerProvider provider) throws IOException {
            jgen.writeStartObject();
            jgen.writeStringField("msgType", "patch");
            jgen.writeStringField("id", patchMessage.documentId());
            jgen.writeStringField("clientId", patchMessage.clientId());
            jgen.writeArrayFieldStart("edits");
            for (JsonMergePatchEdit edit : patchMessage.edits()) {
                if (edit == null) {
                    continue;
                }
                jgen.writeStartObject();
                jgen.writeStringField("clientId", edit.clientId());
                jgen.writeStringField("id", edit.documentId());
                jgen.writeNumberField("clientVersion", edit.clientVersion());
                jgen.writeNumberField("serverVersion", edit.serverVersion());
                jgen.writeStringField("checksum", edit.checksum());
                if (edit.diff() != null) {
                    jgen.writeObjectField("diffs", edit.diff().jsonMergePatch());
                }
                jgen.writeEndObject();
            }
            jgen.writeEndArray();
            jgen.writeEndObject();
        }
    }

    private static class EditDeserializer extends JsonDeserializer<JsonMergePatchEdit> {

        @Override
        public JsonMergePatchEdit deserialize(final JsonParser jp, final DeserializationContext ctxt) throws IOException {
            final ObjectCodec oc = jp.getCodec();
            final JsonNode edit = oc.readTree(jp);
            final Builder eb = JsonMergePatchEdit.withDocumentId(edit.get("id").asText());
            eb.clientId(edit.get("clientId").asText());
            eb.clientVersion(edit.get("clientVersion").asLong());
            eb.serverVersion(edit.get("serverVersion").asLong());
            eb.checksum(edit.get("checksum").asText());
            final JsonNode diffsNode = edit.get("diffs");
            if (!diffsNode.isNull()) {
                try {
                    eb.diff(JsonMergePatch.fromJson(diffsNode));
                } catch (final JsonPatchException e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
            }
            return eb.build();
        }
    }

    private static class EditSerializer extends JsonSerializer<JsonMergePatchEdit> {

        @Override
        public void serialize(final JsonMergePatchEdit edit,
                              final JsonGenerator jgen,
                              final SerializerProvider provider) throws IOException {
            jgen.writeStartObject();
            jgen.writeStringField("msgType", "patch");
            jgen.writeStringField("clientId", edit.clientId());
            jgen.writeStringField("id", edit.documentId());
            jgen.writeNumberField("clientVersion", edit.clientVersion());
            jgen.writeNumberField("serverVersion", edit.serverVersion());
            jgen.writeStringField("checksum", edit.checksum());
            if (edit.diff() != null) {
                jgen.writeObjectField("diffs", edit.diff().jsonMergePatch());
            }
            jgen.writeEndObject();
        }
    }
}

