package com.sekwah.advancedportals.core.api.managers;

import com.google.gson.reflect.TypeToken;
import com.sekwah.advancedportals.core.AdvancedPortalsCore;
import com.sekwah.advancedportals.core.api.portal.AdvancedPortal;
import com.sekwah.advancedportals.core.api.portal.PortalTag;
import com.sekwah.advancedportals.core.api.portal.PortalException;
import com.sekwah.advancedportals.core.api.warphandler.TagHandler;
import com.sekwah.advancedportals.core.data.PlayerLocation;
import com.sekwah.advancedportals.core.data.PortalLocation;
import com.sekwah.advancedportals.core.util.Lang;
import com.sekwah.advancedportals.coreconnector.container.PlayerContainer;

import java.lang.reflect.Type;
import java.util.*;

/**
 * When a player leaves the server any data stored on them is removed to free memory.
 *
 * @author sekwah41
 */
public class PortalManager {

    private final AdvancedPortalsCore portalsCore;

    /**
     * Store data of when the player last entered the portal
     */
    private HashMap<String, Long> lastAttempt = new HashMap();

    /**
     * Tracks the name of portal a player has selected
     */
    private HashMap<String, String> selectedPortal = new HashMap();

    private HashMap<String, PortalLocation> portalSelectorLeftClick = new HashMap();
    private HashMap<String, PortalLocation> portalSelectorRightClick = new HashMap();

    /**
     * Contains all the data for the portals
     */
    private HashMap<String, AdvancedPortal> portalHashMap;

    private AdvancedPortal[] portals;

    public PortalManager(AdvancedPortalsCore portalsCore) {
        this.portalsCore = portalsCore;
    }

    /**
     * A player has left the server
     *
     * @param player
     */
    public void playerLeave(PlayerContainer player) {
        this.lastAttempt.remove(player.getUUID().toString());
        this.selectedPortal.remove(player.getUUID().toString());
        this.portalSelectorLeftClick.remove(player.getUUID().toString());
        this.portalSelectorRightClick.remove(player.getUUID().toString());
    }


    /**
     * Load the default data into the portals.
     */
    public void loadPortals() {
        Type type = new TypeToken<Map<String, AdvancedPortal>>() {
        }.getType();
        this.portalHashMap = this.portalsCore.getDataStorage().loadJson(type, "portals.json");
        if (this.portalHashMap == null) {
            this.portalHashMap = new HashMap<>();
        }
        this.portalsCore.getDataStorage().storeJson(this.portalHashMap, "config.json");
        this.updatePortalArray();
    }

    public void activateCooldown(PlayerContainer player) {
        this.lastAttempt.put(player.getUUID().toString(), System.currentTimeMillis());
    }

    public void playerSelectorActivate(PlayerContainer player, PortalLocation blockLoc, boolean leftClick) {
        int side = leftClick ? 1 : 2;
        if(leftClick) {
            this.portalSelectorLeftClick.put(player.getUUID().toString(), blockLoc);
        }
        else {
            this.portalSelectorRightClick.put(player.getUUID().toString(), blockLoc);
        }
        player.sendMessage(Lang.translateInsertVariablesColor("portal.selector.poschange", side, blockLoc.posX,
                blockLoc.posY, blockLoc.posZ));
    }

    public boolean playerMove(PlayerContainer player, PlayerLocation fromLoc, PlayerLocation toLoc) {
        return false;
    }

    public void createPortal(PlayerContainer player, ArrayList<PortalTag> tags) throws PortalException {
        if (this.portalSelectorLeftClick.containsKey(player.getUUID().toString())
                && this.portalSelectorRightClick.containsKey(player.getUUID().toString())) {
            this.createPortal(player, this.portalSelectorLeftClick.get(player.getUUID().toString()),
                    this.portalSelectorRightClick.get(player.getUUID().toString()), tags);
        } else {
            throw new PortalException(Lang.translate("portal.invalidselection"));
        }
    }

    /**
     *
     * @param loc1
     * @param loc2
     * @param tags
     * @return
     * @throws PortalException
     */
    public boolean createPortal(PortalLocation loc1, PortalLocation loc2, ArrayList<PortalTag> tags) throws PortalException {
        return createPortal(null, loc1, loc2, tags);
    }


    /**
     * Maybe add detection for overlapping however thats not a major issue for now and portals may overlap inside
     * solid blocks
     *
     * @param player null if no player
     * @param loc1
     * @param loc2
     * @param tags
     * @return
     * @throws PortalException
     */
    public boolean createPortal(PlayerContainer player, PortalLocation loc1, PortalLocation loc2, ArrayList<PortalTag> tags) throws PortalException {
        int maxX = Math.max(loc1.posX, loc2.posX);
        int maxY = Math.max(loc1.posY, loc2.posY);
        int maxZ = Math.max(loc1.posZ, loc2.posZ);
        int minX = Math.min(loc1.posX, loc2.posX);
        int minY = Math.min(loc1.posY, loc2.posY);
        int minZ = Math.min(loc1.posZ, loc2.posZ);

        PortalLocation maxLoc = new PortalLocation(maxX, maxY, maxZ);
        PortalLocation minLoc = new PortalLocation(minX, minY, minZ);

        AdvancedPortal portal = new AdvancedPortal(maxLoc, minLoc);
        for(PortalTag portalTag : tags) {
            portal.setArg(portalTag);
        }
        for(PortalTag portalTag : tags) {
            TagHandler.Creation creation = AdvancedPortalsCore.getTagRegistry().getCreationHandler(portalTag.NAME);
            creation.portalCreated(portal, player, portalTag.VALUE);
        }
        String portalName = portal.getArg("name");
        if(portalName == null || portalName.equals("")) {
            throw new PortalException(Lang.translate("portal.noname"));
        }
        else if(this.portalHashMap.containsKey(portalName)) {
            throw new PortalException(Lang.translate("portal.takenname"));
        }
        portal.removeArg("name");
        this.portalHashMap.put(portalName, portal);
        this.updatePortalArray();
        return true;
    }

    private void updatePortalArray() {
        Collection<AdvancedPortal> portalValues = this.portalHashMap.values();
        this.portals = portalValues.toArray(new AdvancedPortal[0]);
    }

    public void removePlayerSelection(PlayerContainer player) throws PortalException {
        String portal = this.selectedPortal.get(player.getUUID());
        if(portal != null) {
            try {
                this.removePortal(player, portal);
            }
            catch(PortalException e) {
                if(e.getMessage().equals(Lang.translate("command.remove.noname"))) {
                    this.selectedPortal.remove(player.getUUID());
                    throw new PortalException(Lang.translate("command.remove.invalidselection"));
                }
                else {
                    throw e;
                }
            }
        }
        throw new PortalException(Lang.translate("command.remove.noselection"));
    }

    /**
     * @param player null if a player didnt send it
     * @param portalName
     * @throws PortalException
     */
    public void removePortal(PlayerContainer player, String portalName) throws PortalException {
        AdvancedPortal portal = this.getPortal(portalName);
        if(portal == null) {
            throw new PortalException(Lang.translate("command.remove.noname"));
        }

    }

    private AdvancedPortal getPortal(String portalName) {
        return this.portalHashMap.get(portalName);
    }

    /**
     * Get an array of portals without a reference to the main array
     * @return
     */
    public Set<Map.Entry<String, AdvancedPortal>> getPortals() {
        return this.portalHashMap.entrySet();
    }

    public boolean inPortalRegion(PlayerLocation loc) {
        return this.inPortalRegion(loc, 0);
    }

    private boolean inPortalRegion(PlayerLocation loc, int additionalArea) {
        return true;
    }

}