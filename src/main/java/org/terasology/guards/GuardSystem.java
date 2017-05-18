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

import org.omg.CORBA.INV_FLAG;
import org.terasology.entitySystem.entity.EntityManager;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.event.ReceiveEvent;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterMode;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.guards.events.GuardKOEvent;
import org.terasology.guards.senseEngine.SightComponent;
import org.terasology.indicators.IndicatorComponent;
import org.terasology.logic.behavior.BehaviorComponent;
import org.terasology.logic.behavior.asset.BehaviorTree;
import org.terasology.logic.characters.MovementMode;
import org.terasology.logic.characters.events.SetMovementModeEvent;
import org.terasology.logic.health.BeforeDestroyEvent;
import org.terasology.logic.inventory.InventoryComponent;
import org.terasology.logic.inventory.ItemComponent;
import org.terasology.logic.inventory.events.DropItemEvent;
import org.terasology.logic.location.LocationComponent;
import org.terasology.registry.In;
import org.terasology.rendering.nui.layers.ingame.inventory.GetItemTooltip;
import org.terasology.rendering.nui.widgets.TooltipLine;
import org.terasology.guards.senseEngine.SightStimulusEvent;
import org.terasology.utilities.Assets;

import java.util.Optional;

/**
 * Created by nikhil on 17/5/17.
 */
@RegisterSystem(RegisterMode.AUTHORITY)
public class GuardSystem  extends BaseComponentSystem {
    @In
    private EntityManager entityManager;

    @ReceiveEvent
    public void beforeDestroy(BeforeDestroyEvent event, EntityRef entity, GuardComponent gc) {
        GuardedAreaComponent guardedAreaComponent = gc.guardedArea.getComponent(GuardedAreaComponent.class);
        if(gc.guardedArea.exists()) {
            if(gc.guardState == GuardState.KO) {
                event.consume();
                return;
            }
            gc.guardState = GuardState.KO;
            entity.saveComponent(gc);
            BehaviorComponent bc = new BehaviorComponent();
            Optional<BehaviorTree> option = Assets.get("pathfinding:idle", BehaviorTree.class);
            if(option.isPresent()) bc.tree = option.get();
            entity.saveComponent(bc);
            entity.send(new SetMovementModeEvent(MovementMode.NONE));
            if(entity.hasComponent(SightComponent.class))
                entity.removeComponent(SightComponent.class);
            if(entity.hasComponent(IndicatorComponent.class))
                entity.removeComponent(IndicatorComponent.class);
            entity.addComponent(new IndicatorComponent("Indicators:knockedOutIndicator"));
            // Drop all items
            if(entity.hasComponent(InventoryComponent.class)) {
                InventoryComponent inventory = entity.getComponent(InventoryComponent.class);
                for(EntityRef item : inventory.itemSlots) {
                    if(item.getId() != 0) {
                        item.send(new DropItemEvent(entity.getComponent(LocationComponent.class).getWorldPosition()));
                    }
                }
                inventory.itemSlots.clear();
                entity.removeComponent(InventoryComponent.class);
            }
            gc.guardedArea.send(new GuardKOEvent(entity));
            event.consume();
        }
    }

    @ReceiveEvent(components =  {GuardComponent.class, InventoryComponent.class})
    public void addInventoryToTooltip(GetItemTooltip event, EntityRef entity, InventoryComponent inventoryComponent) {
        event.getTooltipLines().add(
                new TooltipLine("Items: "));
        for(EntityRef item: inventoryComponent.itemSlots) {
            if(item.getId() != 0)
                event.getTooltipLines().add(
                        new TooltipLine("  " + item.getParentPrefab().getName()));
        }
    }

    @ReceiveEvent
    public void receiveSightStimulus(SightStimulusEvent stimulus, EntityRef entity, GuardComponent gc) {
        if (entity.hasComponent(IndicatorComponent.class)) {
            if (entity.getComponent(IndicatorComponent.class).icon == "Indicators:exclamationIndicator")
                return;
            else
                entity.removeComponent(IndicatorComponent.class);
        }
        entity.addComponent(new IndicatorComponent("Indicators:exclamationIndicator"));
    }

}
