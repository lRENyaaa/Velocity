package com.velocitypowered.proxy.protocol.util;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.api.util.UuidUtils;
import com.velocitypowered.proxy.connection.PlayerDataForwarding;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import static com.velocitypowered.proxy.VelocityServer.GENERAL_GSON;
import static com.velocitypowered.proxy.connection.PlayerDataForwarding.BUNGEE_GUARD_TOKEN_PROPERTY_NAME;
import static com.velocitypowered.proxy.connection.PlayerDataForwarding.LEGACY_SEPARATOR;

public class BungeeHandshakeData {

    /** The name of the BungeeGuard auth token. */
    private static final String BUNGEEGUARD_TOKEN_NAME = "bungeeguard-token";
    /** The key used to define the name of properties in the handshake. */
    private static final String PROPERTY_NAME_KEY = "name";
    /** The key used to define the value of properties in the handshake. */
    private static final String PROPERTY_VALUE_KEY = "value";
    /** The key used to define the signature of properties in the handshake. */
    private static final String PROPERTY_SIGNATURE_KEY = "signature";

    /** The type of the property list in the handshake. */
    private static final Type PROPERTY_LIST_TYPE = new TypeToken<List<JsonObject>>(){}.getType();

    private final String serverHostname;
    private final String socketAddressHostname;
    private final UUID uniqueId;
    private final List<GameProfile.Property> properties;
    private final @Nullable String floodgatePlayerData;
    private final @Nullable String forwardingSecret;

    /**
     * Decodes this handshake from the format used by BungeeCord.
     *
     * @return an bungee handshake data object
     */
    public static BungeeHandshakeData decodeFromString(String handShakeDataString){
        String[] split = handShakeDataString.split("" + LEGACY_SEPARATOR);

        int offset = 0;

        String serverHostName = split[0];

        String floodgatePlayerData = split[1].startsWith("^Floodgate^") ? split[1] : null;
        if (floodgatePlayerData != null) offset++;

        String socketAddressHostname = split[1 + offset];
        UUID uniqueId = UUID.fromString(split[2 + offset].replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));

        List<JsonObject> propertyJsonObjects = new LinkedList<>(GENERAL_GSON.fromJson(split[3 + offset], PROPERTY_LIST_TYPE));

        String forwardingSecret = null;
        ImmutableList.Builder<GameProfile.Property> builder = ImmutableList.builder();


        for (JsonObject propertyJsonObject : propertyJsonObjects){
            GameProfile.Property property = readFromJsonObject(propertyJsonObject);
            if (property.getName().equals(BUNGEEGUARD_TOKEN_NAME)) {
                forwardingSecret = property.getValue();
                continue;
            }

            builder.add(property);
        }

        List<GameProfile.Property> properties = builder.build();

        return new BungeeHandshakeData(serverHostName, socketAddressHostname, uniqueId, properties, floodgatePlayerData, forwardingSecret);
    }

    private static GameProfile.Property readFromJsonObject(JsonObject object){
        String name = object.get(PROPERTY_NAME_KEY).getAsString();
        String value = object.get(PROPERTY_VALUE_KEY).getAsString();
        String signature = object.get(PROPERTY_SIGNATURE_KEY).getAsString();

        return new GameProfile.Property(name, value, signature);
    }

    private BungeeHandshakeData(String serverHostname, String socketAddressHostname, UUID uniqueId, List<GameProfile.Property> properties, @Nullable String floodgatePlayerData, @Nullable String forwardingSecret) {
        this.serverHostname = serverHostname;
        this.socketAddressHostname = socketAddressHostname;
        this.uniqueId = uniqueId;
        this.properties = properties;
        this.floodgatePlayerData = floodgatePlayerData;
        this.forwardingSecret = forwardingSecret;
    }

    public String serverHostname() {
        return this.serverHostname;
    }

    public String socketAddressHostname() {
        return this.socketAddressHostname;
    }

    public UUID uniqueId() {
        return this.uniqueId;
    }

    public List<GameProfile.Property> properties() {
        return this.properties;
    }

    public @Nullable String floodgatePlayerData() {
        return this.floodgatePlayerData;
    }

    public @Nullable String forwardingSecret() {
        return this.forwardingSecret;
    }

    public GameProfile genrateGameProfile(String name) {
        return new GameProfile(uniqueId, name, properties);
    }

    @Override
    public String toString() {
        return "BungeeHandshakeData{"
               + "serverHostName=" + serverHostname
               + ", socketAddressHostname='" + socketAddressHostname
               + ", uniqueId='" + uniqueId
               + ", properties=" + GENERAL_GSON.toJson(properties)
               + ", floodgatePlayerData=" + floodgatePlayerData
               + ", forwardingSecret=" + forwardingSecret
               + '}';
    }

    /**
     * Re-encodes this handshake to the format used by BungeeCord.
     *
     * @return an encoded string for the handshake
     */
    public String encode(final @Nullable String forwardingSecret) {
        // BungeeCord IP forwarding is simply a special injection after the "address" in the handshake,
        // separated by \0 (the null byte). In order, you send the original host, the player's IP, their
        // UUID (undashed), and if you are in online-mode, their login properties (from Mojang).
        final StringBuilder data = new StringBuilder();
        data.append(serverHostname);
        data.append(LEGACY_SEPARATOR);

        if (floodgatePlayerData != null){
            data.append(floodgatePlayerData);
            data.append(LEGACY_SEPARATOR);
        }

        data.append(socketAddressHostname);
        data.append(LEGACY_SEPARATOR);
        data.append(UuidUtils.toUndashed(uniqueId));
        data.append(LEGACY_SEPARATOR);

        List<GameProfile.Property> encodeProperties = addForwardingSecret(properties, forwardingSecret == null ? this.forwardingSecret : forwardingSecret);

        GENERAL_GSON.toJson(encodeProperties, data);

        return data.toString();
    }

    private static List<GameProfile.Property> addForwardingSecret(List<GameProfile.Property> properties, @Nullable String forwardingSecret) {
        if (forwardingSecret == null) return properties;

        final GameProfile.Property property = new GameProfile.Property(
                BUNGEE_GUARD_TOKEN_PROPERTY_NAME,
                forwardingSecret,
                ""
        );

        return ImmutableList.<GameProfile.Property>builder()
                .addAll(properties)
                .add(property)
                .build();
    }

}
