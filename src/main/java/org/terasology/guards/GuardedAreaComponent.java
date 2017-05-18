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

import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.Component;
import org.terasology.math.geom.Vector3i;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by nikhil on 15/5/17.
 */
public class GuardedAreaComponent implements Component {
    public Vector3i center;
    public int radius;
    public List<String> guardTypeList;     // list of prefab names
    public List<Integer> guardMultiplicities;  // number of guards of each type

    public Map<Long, EntityRef> guards;
    public List<EntityRef> KOGuards = new ArrayList<>();  // List of knocked out guards
    public boolean[][][] walkableBlockMatrix;
    private GuardedAreaState state = GuardedAreaState.CREATED;

    public void setState(GuardedAreaState state){
        this.state = state;
    }

    public GuardedAreaState getState(){
        return state;
    }

}
