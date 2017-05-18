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
package org.terasology.guards.senseEngine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.entitySystem.entity.EntityManager;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterMode;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.entitySystem.systems.UpdateSubscriberSystem;
import org.terasology.indicators.IndicatorComponent;
import org.terasology.logic.location.LocationComponent;
import org.terasology.math.Direction;
import org.terasology.math.geom.Quat4f;
import org.terasology.math.geom.Vector3f;
import org.terasology.network.ClientComponent;
import org.terasology.physics.HitResult;
import org.terasology.physics.Physics;
import org.terasology.physics.StandardCollisionGroup;
import org.terasology.registry.In;

/**
 * Created by nikhil on 17/5/17.
 */

/**
 * For now this system only checks if player is within sight cone of the entity having the
 * SightComponent and sends a SightStimulusEvent to the entity if anything was spotted.
 */
@RegisterSystem(RegisterMode.AUTHORITY)
public class SightSenseSystem extends BaseComponentSystem implements UpdateSubscriberSystem {
    @In
    private EntityManager entityManager;
    @In
    private Physics physicsRenderer;

    private static final Logger logger = LoggerFactory.getLogger(SightSenseSystem.class);

    /**
     * @param x coordinates of point to be tested
     * @param apex coordinates of apex point of cone
     * @param direction direction of sight
     * @param aperture in degrees
     */
    private boolean isLyingInCone(Vector3f x, Vector3f apex, Vector3f direction,
                                        float aperture){

        //convert to radians
        aperture *= 0.0174533f;
        float halfAperture = aperture/2.f;

        // Vector pointing to X point from apex
        Vector3f apexToXVect = new Vector3f(x);
        apexToXVect.sub(apex);

        return apexToXVect.dot(direction)
                /apexToXVect.length()/direction.length()
                >
                Math.cos(halfAperture);
    }


    @Override
    public void update(float delta) {
        for(EntityRef entity: entityManager.getEntitiesWith(SightComponent.class)) {
            if(entity.hasComponent(IndicatorComponent.class))
                entity.removeComponent(IndicatorComponent.class);
            Vector3f entityPosition = entity.getComponent(LocationComponent.class).getWorldPosition();
            Quat4f entityOrientation = entity.getComponent(LocationComponent.class).getWorldRotation();
            Vector3f direction = Direction.BACKWARD.getVector3f();
            direction = entityOrientation.rotate(direction, direction);
            SightComponent sightComponent = entity.getComponent(SightComponent.class);
            Iterable<EntityRef> clients = entityManager.getEntitiesWith(ClientComponent.class);
            for(EntityRef client : clients) {
                Vector3f clientPosition = client.getComponent(LocationComponent.class).getWorldPosition();
                float distance = entityPosition.distance(clientPosition);
                if( distance > sightComponent.maxDistance )
                    continue;
                if( !isLyingInCone(clientPosition, entityPosition,
                        direction, sightComponent.aperture) )
                    continue;
                HitResult hr = physicsRenderer.rayTrace(entityPosition, clientPosition.sub(entityPosition), distance, StandardCollisionGroup.WORLD);
                //TODO bug in raytrace? returns hit true for distance greater than maxDistance
                if(hr.isWorldHit() && entityPosition.distanceSquared(hr.getHitPoint()) < distance * distance)
                    continue;
                float distanceFactor = 1 - distance/sightComponent.maxDistance;
                //TODO make visibility depend on lighting
                float visibility = 1f;
                float confidence = distanceFactor * visibility;
                entity.send(new SightStimulusEvent(client, confidence));
            }
        }
    }
}
