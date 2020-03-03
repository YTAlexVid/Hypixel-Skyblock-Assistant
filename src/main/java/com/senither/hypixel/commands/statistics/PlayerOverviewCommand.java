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

package com.senither.hypixel.commands.statistics;

import com.google.gson.JsonObject;
import com.senither.hypixel.Constants;
import com.senither.hypixel.SkyblockAssistant;
import com.senither.hypixel.chat.MessageFactory;
import com.senither.hypixel.chat.PlaceholderMessage;
import com.senither.hypixel.contracts.commands.SkillCommand;
import com.senither.hypixel.rank.items.Collection;
import com.senither.hypixel.utils.NumberUtil;
import net.dv8tion.jda.api.entities.Message;
import net.hypixel.api.reply.PlayerReply;
import net.hypixel.api.reply.skyblock.SkyBlockProfileReply;

import java.util.Arrays;
import java.util.List;

public class PlayerOverviewCommand extends SkillCommand {

    public PlayerOverviewCommand(SkyblockAssistant app) {
        super(app, "Overview");
    }

    @Override
    public String getName() {
        return "Player Overview Statistics";
    }

    @Override
    public List<String> getDescription() {
        return Arrays.asList(
            "Gets a list of general information about the users profile, including",
            "average skill level, slayer, minions, collections, and coins."
        );
    }

    @Override
    public List<String> getUsageInstructions() {
        return Arrays.asList(
            "`:command <username> [profile]` - Gets skill stats for the given username",
            "`:command <mention> [profile]` - Gets skill stats for the mentioned Discord user"
        );
    }

    @Override
    public List<String> getTriggers() {
        return Arrays.asList("player", "profile", "overview");
    }

    @Override
    protected void handleSkyblockProfile(Message message, SkyBlockProfileReply profileReply, PlayerReply playerReply) {
        JsonObject member = getProfileMemberFromPlayer(profileReply, playerReply);

        final PlaceholderMessage placeholderMessage = MessageFactory.makeSuccess(
            message, ""
        ).setTitle(getUsernameFromPlayer(playerReply) + "'s Profile Overview");

        message.editMessage(placeholderMessage
            .addField("Average Skill Level", NumberUtil.formatNicelyWithDecimals(getAverageSkillLevel(playerReply, member)), true)
            .addField("Collection", getCompletedCollections(member), true)
            .addField("Pets", NumberUtil.formatNicely(member.get("pets").getAsJsonArray().size()), true)
            .addField("Minion Slots", getMinionSlots(member), true)
            .addField("Coins", getCoins(profileReply, member), true)
            .addField("Slayer", getTotalSlayerXp(member), true)
            .setFooter(String.format("Profile: %s", profileReply.getProfile().get("cute_name").getAsString()))
            .buildEmbed()
        ).queue();
    }

    private String getTotalSlayerXp(JsonObject member) {
        JsonObject slayerBosses = member.getAsJsonObject("slayer_bosses");

        try {
            int totalExp = 0;

            for (String type : slayerBosses.keySet()) {
                totalExp += getEntryFromSlayerData(slayerBosses.getAsJsonObject(type).getAsJsonObject(), "xp");
            }

            return NumberUtil.formatNicely(totalExp) + " Total XP";
        } catch (Exception e) {
            return "No Slayer to Display";
        }
    }

    private String getCoins(SkyBlockProfileReply profileReply, JsonObject member) {
        double coinsInPurse = member.get("coin_purse").getAsDouble();
        if (!profileReply.getProfile().has("banking")) {
            return NumberUtil.formatNicelyWithDecimals(coinsInPurse) + " (API is Disabled)";
        }
        return NumberUtil.formatNicelyWithDecimals(profileReply.getProfile().get("banking").getAsJsonObject().get("balance").getAsDouble() + coinsInPurse);
    }

    private String getCompletedCollections(JsonObject member) {
        if (!member.has("collection")) {
            return "API is Disabled";
        }

        JsonObject playerCollection = member.get("collection").getAsJsonObject();

        int completedCollections = 0;
        for (Collection collection : Collection.values()) {
            if (!playerCollection.has(collection.getKey())) {
                continue;
            }

            if (playerCollection.get(collection.getKey()).getAsLong() >= collection.getMaxLevelExperience()) {
                completedCollections++;
            }
        }

        return String.format("%s / %s", completedCollections, Collection.values().length);
    }

    private String getMinionSlots(JsonObject member) {
        int craftedMinions = member.get("crafted_generators").getAsJsonArray().size();

        int minionSlots = craftedMinions < 5 ? 5
            : (int) (craftedMinions < 15 ? 6
            : craftedMinions < 30 ? 7
            : craftedMinions < 50 ? 8
            : craftedMinions < 300 ? 9 + Math.floor((craftedMinions - 50) / 25)
            : 19 + Math.floor((craftedMinions - 300) / 50));

        return String.format("%s _(%s/572 Crafts)_",
            minionSlots, craftedMinions
        );
    }

    private double getAverageSkillLevel(PlayerReply playerReply, JsonObject member) {
        double mining = getDoubleFromJson(member, "experience_skill_mining");
        double foraging = getDoubleFromJson(member, "experience_skill_foraging");
        double enchanting = getDoubleFromJson(member, "experience_skill_enchanting");
        double farming = getDoubleFromJson(member, "experience_skill_farming");
        double combat = getDoubleFromJson(member, "experience_skill_combat");
        double fishing = getDoubleFromJson(member, "experience_skill_fishing");
        double alchemy = getDoubleFromJson(member, "experience_skill_alchemy");

        if (mining + foraging + enchanting + farming + combat + fishing + alchemy == 0) {
            return getAverageSkillLevelFromAchievements(playerReply);
        }
        return (getSkillLevelFromExperience(mining) +
            getSkillLevelFromExperience(foraging) +
            getSkillLevelFromExperience(enchanting) +
            getSkillLevelFromExperience(farming) +
            getSkillLevelFromExperience(combat) +
            getSkillLevelFromExperience(fishing) +
            getSkillLevelFromExperience(alchemy)
        ) / 7D;
    }

    private double getAverageSkillLevelFromAchievements(PlayerReply playerReply) {
        JsonObject achievements = playerReply.getPlayer().get("achievements").getAsJsonObject();

        double mining = getDoubleFromJson(achievements, "skyblock_excavator");
        double foraging = getDoubleFromJson(achievements, "skyblock_gatherer");
        double enchanting = getDoubleFromJson(achievements, "skyblock_augmentation");
        double farming = getDoubleFromJson(achievements, "skyblock_harvester");
        double combat = getDoubleFromJson(achievements, "skyblock_combat");
        double fishing = getDoubleFromJson(achievements, "skyblock_angler");
        double alchemy = getDoubleFromJson(achievements, "skyblock_concoctor");

        return (getSkillLevelFromExperience(mining) +
            getSkillLevelFromExperience(foraging) +
            getSkillLevelFromExperience(enchanting) +
            getSkillLevelFromExperience(farming) +
            getSkillLevelFromExperience(combat) +
            getSkillLevelFromExperience(fishing) +
            getSkillLevelFromExperience(alchemy)
        ) / 7D;
    }

    private double getDoubleFromJson(JsonObject object, String name) {
        try {
            return object.get(name).getAsDouble();
        } catch (Exception e) {
            return 0D;
        }
    }

    private int getEntryFromSlayerData(JsonObject jsonObject, String entry) {
        try {
            return jsonObject.get(entry).getAsInt();
        } catch (Exception e) {
            return 0;
        }
    }

    private double getSkillLevelFromExperience(double experience) {
        int level = 0;
        for (int toRemove : Constants.GENERAL_SKILL_EXPERIENCE) {
            experience -= toRemove;
            if (experience < 0) {
                return level + (1D - (experience * -1) / (double) toRemove);
            }
            level++;
        }
        return level;
    }
}
