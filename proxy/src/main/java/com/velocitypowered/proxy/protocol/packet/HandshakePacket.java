/*
 * Copyright (C) 2018-2021 Velocity Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.velocitypowered.proxy.protocol.packet;

import static com.velocitypowered.proxy.connection.PlayerDataForwarding.LEGACY_SEPARATOR;
import static com.velocitypowered.proxy.connection.forge.legacy.LegacyForgeConstants.HANDSHAKE_HOSTNAME_TOKEN;

import com.velocitypowered.api.network.HandshakeIntent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.util.BungeeHandshakeData;
import com.velocitypowered.proxy.protocol.util.NettyPreconditions;
import io.netty.buffer.ByteBuf;

public class HandshakePacket implements MinecraftPacket {

  // This size was chosen to ensure Forge clients can still connect even with very long hostnames.
  // While DNS technically allows any character to be used, in practice ASCII is used.
  private static final int MAXIMUM_HOSTNAME_LENGTH = 255 + HANDSHAKE_HOSTNAME_TOKEN.length() + 1;
  private ProtocolVersion protocolVersion;
  private BungeeHandshakeData bungeeHandshakeData = null;
  private String serverAddress = "";
  private int port;
  private HandshakeIntent intent;
  private int nextStatus;

  public ProtocolVersion getProtocolVersion() {
    return protocolVersion;
  }

  public void setProtocolVersion(ProtocolVersion protocolVersion) {
    this.protocolVersion = protocolVersion;
  }

  public BungeeHandshakeData getBungeeHandshakeData() {
    return bungeeHandshakeData;
  }

  public String getServerAddress() {
    return serverAddress;
  }

  public void setServerAddress(String serverAddress) {
    this.serverAddress = serverAddress;
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public int getNextStatus() {
    return this.nextStatus;
  }

  public void setIntent(HandshakeIntent intent) {
    this.intent = intent;
    this.nextStatus = intent.id();
  }

  public HandshakeIntent getIntent() {
    return this.intent;
  }

  @Override
  public String toString() {
    return "Handshake{"
        + "protocolVersion=" + protocolVersion
        + ", bungeeHandShakeData='" + (bungeeHandshakeData == null ? "null" : bungeeHandshakeData.toString())
        + ", serverAddress='" + serverAddress + '\''
        + ", port=" + port
        + ", nextStatus=" + nextStatus
        + '}';
  }

  @Override
  public void decode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion ignored) {
    int realProtocolVersion = ProtocolUtils.readVarInt(buf);
    this.protocolVersion = ProtocolVersion.getProtocolVersion(realProtocolVersion);
    this.bungeeHandshakeData = decode(ProtocolUtils.readString(buf));
    this.serverAddress = getServerAddress(buf,bungeeHandshakeData);
    this.port = buf.readUnsignedShort();
    this.nextStatus = ProtocolUtils.readVarInt(buf);
    this.intent = HandshakeIntent.getById(nextStatus);
  }

  @Override
  public void encode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion ignored) {
    ProtocolUtils.writeVarInt(buf, this.protocolVersion.getProtocol());
    ProtocolUtils.writeString(buf, this.serverAddress);
    buf.writeShort(this.port);
    ProtocolUtils.writeVarInt(buf, this.nextStatus);
  }

  @Override
  public boolean handle(MinecraftSessionHandler handler) {
    return handler.handle(this);
  }

  private static String getServerAddress(ByteBuf buf, BungeeHandshakeData handShakeData) {
    if (handShakeData == null) {
      return ProtocolUtils.readString(buf, MAXIMUM_HOSTNAME_LENGTH);
    }

    String address = handShakeData.serverHostname();
    NettyPreconditions.checkFrame(address.length() <= MAXIMUM_HOSTNAME_LENGTH, "Got a too-long string (got %s, max %s)", address.length(), MAXIMUM_HOSTNAME_LENGTH);

    return address;
  }

  private static BungeeHandshakeData decode(String string) {
    if (string.split("" + LEGACY_SEPARATOR).length < 4) return null;

    try {
      return BungeeHandshakeData.decodeFromString(string);
    } catch (Exception e) {
      e.printStackTrace();
      throw e;
    }
  }
}
