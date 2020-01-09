/*
 * Copyright (c) 2020.
 *
 * This file is part of Hypixel Skyblock Assistant.
 *
 * Hypixel Guild Synchronizer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Hypixel Guild Synchronizer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Hypixel Guild Synchronizer.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package com.senither.hypixel.database.controller;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.senither.hypixel.database.DatabaseManager;
import com.senither.hypixel.database.collection.Collection;
import com.senither.hypixel.database.collection.DataRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

public class GuildController {

    private static final Logger log = LoggerFactory.getLogger(GuildController.class);

    private static final Cache<Long, GuildEntry> cache = CacheBuilder.newBuilder()
        .expireAfterAccess(60, TimeUnit.SECONDS)
        .build();

    public static GuildEntry getGuildById(DatabaseManager manager, long guildId) {
        GuildEntry cacheEntry = cache.getIfPresent(guildId);
        if (cacheEntry != null) {
            log.debug("Found guild entry for {} using the in-memory cache", guildId);
            return cacheEntry;
        }

        try {
            Collection result = manager.query("SELECT * FROM `guilds` WHERE `discord_id` = ?", guildId);
            if (result.isEmpty()) {
                return null;
            }

            log.debug("Found guild entry for {} using the database cache", guildId);

            GuildEntry guildEntry = new GuildEntry(result.first());
            cache.put(guildId, guildEntry);

            return guildEntry;
        } catch (SQLException e) {
            log.debug("Found no guild entry for {} in any cache provider!", guildId);

            return null;
        }
    }

    public static boolean deleteGuildWithId(DatabaseManager manager, long guildId) {
        try {
            manager.queryUpdate("DELETE FROM `guilds` WHERE `discord_id` = ?", guildId);

            cache.invalidate(guildId);

            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    public static void forgetCacheFor(long guildId) {
        cache.invalidate(guildId);
    }

    public static class GuildEntry {

        private final String id;
        private final long discordId;
        private final String name;
        private final String data;
        private final Long defaultRole;
        private final boolean autoRename;

        GuildEntry(DataRow row) {
            id = row.getString("id");
            discordId = row.getLong("discord_id");
            name = row.getString("name");
            data = row.getString("data");
            autoRename = row.getBoolean("auto_rename");

            long defaultRole = row.getLong("default_role", 0L);
            this.defaultRole = defaultRole == 0L ? null : defaultRole;
        }

        public String getId() {
            return id;
        }

        public long getDiscordId() {
            return discordId;
        }

        public String getName() {
            return name;
        }

        public String getData() {
            return data;
        }

        public Long getDefaultRole() {
            return defaultRole;
        }

        public boolean isAutoRename() {
            return autoRename;
        }
    }
}
