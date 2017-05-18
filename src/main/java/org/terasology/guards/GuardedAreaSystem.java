/*
 * Copyright 2017 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terasology.guards;

import org.terasology.entitySystem.event.ReceiveEvent;
import org.terasology.guards.events.GuardKOEvent;
import org.terasology.guards.senseEngine.SightComponent;
import org.terasology.assets.management.AssetManager;
import org.terasology.engine.Time;
import org.terasology.entitySystem.entity.EntityManager;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.prefab.Prefab;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterMode;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.entitySystem.systems.UpdateSubscriberSystem;
import org.terasology.logic.behavior.BehaviorComponent;
import org.terasology.logic.behavior.asset.BehaviorTree;
import org.terasology.logic.inventory.InventoryComponent;
import org.terasology.logic.location.LocationComponent;
import org.terasology.math.geom.Vector3f;
import org.terasology.math.geom.Vector3i;
import org.terasology.navgraph.NavGraphSystem;
import org.terasology.registry.In;
import org.terasology.utilities.Assets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;

/**
 * Created by nikhil on 15/5/17.
 */
enum GuardedAreaState{CREATED, INITIALIZING, MAPPING_AREA, RUNNING, CONQUERED}
enum GuardState{KO, SLEEPING}

@RegisterSystem(RegisterMode.AUTHORITY)
public class GuardedAreaSystem  extends BaseComponentSystem implements UpdateSubscriberSystem {
    @In
    private EntityManager entityManager;
    @In
    private Time time;
    @In
    private AssetManager assetManager;
    @In
    private NavGraphSystem navGraphSystem;

    boolean flag = false;

    @ReceiveEvent
    public void OnGuardKO(GuardKOEvent event, EntityRef gA, GuardedAreaComponent gAC) {
        gAC.KOGuards.add(gAC.guards.remove(event.guard.getId()));
        if(gAC.guards.isEmpty()) {
            for(EntityRef guard : gAC.KOGuards) {
                guard.destroy();
            }
            gA.destroy();
        }
        else
            gA.saveComponent(gAC);
    }

    private BehaviorTree getBehavior(){
        Optional<BehaviorTree> option = Assets.get("pathfinding:stray", BehaviorTree.class);
        if(option.isPresent()) return option.get();
        return null;
    }

    private void initialiseInventory(EntityRef guard, int numSlots) {
        //TODO can we use the built-in inventory component?
        InventoryComponent inventoryComponent = new InventoryComponent(numSlots);
        inventoryComponent.itemSlots.set(0, entityManager.create("core:pickaxe"));
        inventoryComponent.itemSlots.set(1, entityManager.create("core:crossbow"));
        guard.saveComponent(inventoryComponent);
    }


    private void spawnGuards(EntityRef gA, GuardedAreaComponent gAC, ArrayList<Prefab> guardPrefabs,
                     ArrayList<BehaviorTree> behaviorTrees, ArrayList<Vector3f> positions) {
        gAC.guards = new HashMap<>(guardPrefabs.size());
        for (int i = 0; i < guardPrefabs.size(); i++) {
            EntityRef guard = entityManager.create(guardPrefabs.get(i), positions.get(i));
            if(guard.hasComponent(InventoryComponent.class))
                initialiseInventory(guard, 3);
            guard.addComponent(new SightComponent());
            GuardComponent gc = guard.getComponent(GuardComponent.class);
            gc.guardedArea = gA;
            guard.saveComponent(gc);
            BehaviorComponent behaviorComponent = new BehaviorComponent();
            behaviorComponent.tree = getBehavior();
            if(getBehavior() != null) guard.addOrSaveComponent(behaviorComponent);
            gAC.guards.put(guard.getId(), guard);
        }

        gA.saveComponent(gAC);
    }

    private ArrayList<Prefab> getGuardPrefabs(GuardedAreaComponent gAC) {
        ArrayList<Prefab> guardPrefabs = new ArrayList<>();
        for (int i = 0; i < gAC.guardTypeList.size(); i++) {
            Prefab guardPrefab = Assets.getPrefab(gAC.guardTypeList.get(i)).get();
            for (int j = 0; j < gAC.guardMultiplicities.get(i); j++) {
                guardPrefabs.add(guardPrefab);
            }
        }
        return  guardPrefabs;
    }

    private void initialiseArea(EntityRef gA, GuardedAreaComponent gAC) {
        ArrayList<Prefab> guardPrefabs = getGuardPrefabs(gAC);
        //gAC.walkableBlockMatrix = getWalkableBlockMatrix(gAC.center, gAC.radius);
        ArrayList<Vector3f> positions = new ArrayList<>();
        for (int i = 0; i < guardPrefabs.size(); i++) {
            positions.add(new Vector3f(5,2.5f,0));
        }
        positions.add(new Vector3f(6,2.5f,0));
        spawnGuards(gA, gAC, guardPrefabs, null, positions);
    }

    private boolean[][][] getWalkableBlockMatrix(Vector3i center, int radius) {
        boolean[][][] wbm = new boolean[2 * radius][2 * radius][2 * radius];
        Vector3i corner1 = new Vector3i(center.x - radius, center.y - radius, center.z - radius);
        Vector3i corner2 = new Vector3i(center.x + radius, center.y + radius, center.z + radius);

        for (int y = corner1.y; y < corner2.y; y++) {
            for (int x = corner1.x; x < corner2.x; x++) {
                for (int z = corner1.z; z < corner2.z; z++) {
                    if(navGraphSystem.getBlock(new Vector3i(x, y, z)) != null)
                        wbm[y][x][z] = true;
                }
            }
        }

        return wbm;
    }

    public void update(float delta) {
       // if(!flag) {entityManager.create("Guards:sampleguardedarea");flag=true;}
        for (EntityRef gA : entityManager.getEntitiesWith(GuardedAreaComponent.class)) {
            GuardedAreaComponent gAC = gA.getComponent(GuardedAreaComponent.class);
            if(gAC.getState() == GuardedAreaState.CREATED) {
                gAC.setState(GuardedAreaState.INITIALIZING);
                Vector3f position = gA.getComponent(LocationComponent.class).getWorldPosition();
                gAC.center = new Vector3i(position.x, position.y, position.z);
                gA.saveComponent(gAC);
                initialiseArea(gA, gAC);
                gAC.setState(GuardedAreaState.RUNNING);
            }
            if(gAC.getState() == GuardedAreaState.RUNNING) {

            }
        }
    }

}
