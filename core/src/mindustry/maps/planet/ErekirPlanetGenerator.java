package mindustry.maps.planet;

import arc.graphics.*;
import arc.math.*;
import arc.math.geom.*;
import arc.util.*;
import arc.util.noise.*;
import mindustry.ai.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.maps.generators.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.environment.*;
import mindustry.world.meta.*;

import static mindustry.Vars.*;

public class ErekirPlanetGenerator extends PlanetGenerator{
    public float scl = 2f;
    public float heightScl = 0.9f, octaves = 8, persistence = 0.7f, heightPow = 3f, heightMult = 1.6f;

    //TODO inline/remove
    public static float arkThresh = 0.28f, arkScl = 0.83f;
    public static int arkSeed = 7, arkOct = 2;
    public static float liqThresh = 0.64f, liqScl = 87f, redThresh = 3.1f, noArkThresh = 0.3f;

    Block[] terrain = {Blocks.regolith, Blocks.regolith, Blocks.regolith, Blocks.regolith, Blocks.yellowStone, Blocks.rhyolite, Blocks.carbonStone};

    {
        baseSeed = 2;
    }

    @Override
    public void generateSector(Sector sector){
        //no bases right now
    }

    @Override
    public float getHeight(Vec3 position){
        return Mathf.pow(rawHeight(position), heightPow) * heightMult;
    }

    @Override
    public Color getColor(Vec3 position){
        Block block = getBlock(position);
        return Tmp.c1.set(block.mapColor).a(1f - block.albedo);
    }

    @Override
    public float getSizeScl(){
        return 2000 * 1.06f;
    }

    @Override
    public Schematic getDefaultLoadout(){
        return Loadouts.basicBastion;
    }

    float rawHeight(Vec3 position){
        return Simplex.noise3d(seed, octaves, persistence, 1f/heightScl, 10f + position.x, 10f + position.y, 10f + position.z);
    }

    float rawTemp(Vec3 position){
        return position.dst(0, 0, 1)*2.2f - Simplex.noise3d(seed, 8, 0.54f, 1.4f, 10f + position.x, 10f + position.y, 10f + position.z) * 2.9f;
    }

    Block getBlock(Vec3 position){
        float ice = rawTemp(position);
        Tmp.v32.set(position);

        float height = rawHeight(position);
        Tmp.v31.set(position);
        position = Tmp.v33.set(position).scl(scl);
        //float temp = Simplex.noise3d(seed, 8, 0.6, 1f/2f, 10f + position.x, 10f + position.y + 99f, 10f + position.z);
        //Mathf.clamp((int)(temp * arr.length), 0, arr[0].length - 1)
        height *= 1.2f;
        height = Mathf.clamp(height);

        Block result = terrain[Mathf.clamp((int)(height * terrain.length), 0, terrain.length - 1)];

        //TODO veins?
        if(ice < 0.3){
            return Blocks.crystallineStone;
        }

        if(ice < 0.6){
            if(result == Blocks.rhyolite || result == Blocks.yellowStone || result == Blocks.regolith){
                //TODO bio(?) luminescent stuff? ice?
                return Blocks.carbonStone; //TODO perhaps something else.
            }
        }

        position = Tmp.v32;

        //TODO tweak this to make it more natural
        //TODO edge distortion?
        if(ice < redThresh - noArkThresh && Ridged.noise3d(seed + arkSeed, position.x + 2f, position.y + 8f, position.z + 1f, arkOct, arkScl) > arkThresh){
            //TODO arkyic in middle
            result = Blocks.beryllicStone;
        }

        if(ice > redThresh){
            result = Blocks.redStone;
        }else if(ice > redThresh - 0.4f){
            //TODO this may increase the amount of regolith, but it's too obvious a transition.
            result = Blocks.regolith;
        }

        return result;
    }

    @Override
    public void genTile(Vec3 position, TileGen tile){
        tile.floor = getBlock(position);

        if(tile.floor == Blocks.rhyolite && rand.chance(0.01)){
            tile.floor = Blocks.rhyoliteCrater;
        }

        tile.block = tile.floor.asFloor().wall;

        if(Ridged.noise3d(seed + 1, position.x, position.y, position.z, 2, 14) > 0.14){
            tile.block = Blocks.air;
        }

        //TODO only certain places should have carbon stone...
        if(Ridged.noise3d(seed + 2, position.x, position.y + 4f, position.z, 3, 6f) > 0.6){
            tile.floor = Blocks.carbonStone;
        }
    }

    @Override
    protected void generate(){
        float temp = rawTemp(sector.tile.v);

        if(temp > 0.7){

            pass((x, y) -> {
                if(floor != Blocks.redIce){
                    float noise = noise(x + 782, y, 7, 0.8f, 280f, 1f);
                    if(noise > 0.62f){
                        if(noise > 0.635f){
                            floor = Blocks.slag;
                        }else{
                            floor = Blocks.yellowStone;
                        }
                        ore = Blocks.air;
                    }

                    if(noise > 0.5f && floor == Blocks.beryllicStone){
                        floor = Blocks.yellowStone;
                    }
                }
            });
        }

        cells(4);

        //regolith walls for more dense terrain
        pass((x, y) -> {
            if(floor == Blocks.regolith && noise(x, y, 3, 0.4f, 13f, 1f) > 0.59f){
                block = Blocks.regolithWall;
            }
        });

        //TODO: yellow regolith biome tweaks
        //TODO: crystal biome on the dark side w/ terrain tweaks
        //TODO ice biome

        float length = width/3f;
        Vec2 trns = Tmp.v1.trns(rand.random(360f), length);
        int
        spawnX = (int)(trns.x + width/2f), spawnY = (int)(trns.y + height/2f),
        endX = (int)(-trns.x + width/2f), endY = (int)(-trns.y + height/2f);
        float maxd = Mathf.dst(width/2f, height/2f);

        erase(spawnX, spawnY, 15);
        brush(pathfind(spawnX, spawnY, endX, endY, tile -> (tile.solid() ? 300f : 0f) + maxd - tile.dst(width/2f, height/2f)/10f, Astar.manhattan), 9);
        erase(endX, endY, 15);

        //arkycite
        pass((x, y) -> {
            if(floor != Blocks.beryllicStone) return;

            //TODO bad
            if(Math.abs(noise(x, y + 500f, 5, 0.6f, 40f, 1f) - 0.5f) < 0.09f){
                floor = Blocks.arkyicStone;
            }

            if(nearWall(x, y)) return;

            float noise = noise(x + 300, y - x*1.6f + 100, 4, 0.8f, liqScl, 1f);

            if(noise > liqThresh){
                floor = Blocks.arkyciteFloor;
            }
        });

        median(2, 0.6, Blocks.arkyciteFloor);

        blend(Blocks.arkyciteFloor, Blocks.arkyicStone, 4);

        //TODO may overwrite floor blocks under walls and look bad
        blend(Blocks.slag, Blocks.yellowStonePlates, 4);

        distort(10f, 12f);

        distort(5f, 7f);

        //does arkycite need smoothing?
        median(2, 0.6, Blocks.arkyciteFloor);

        //smooth out slag to prevent random 1-tile patches
        median(3, 0.6, Blocks.slag);

        pass((x, y) -> {
            //rough rhyolite
            if(noise(x, y + 600 + x, 5, 0.86f, 60f, 1f) < 0.41f && floor == Blocks.rhyolite){
                floor = Blocks.roughRhyolite;
            }

            if((floor == Blocks.arkyciteFloor || floor == Blocks.arkyicStone) && block.isStatic()){
                block = Blocks.arkyicWall;
            }

            float max = 0;
            for(Point2 p : Geometry.d8){
                max = Math.max(max, world.getDarkness(x + p.x, y + p.y));
            }
            if(max > 0){
                block = floor.asFloor().wall;
                if(block == Blocks.air) block = Blocks.yellowStoneWall;
            }

            if(floor == Blocks.yellowStonePlates && noise(x + 78 + y, y, 3, 0.8f, 6f, 1f) > 0.44f){
                floor = Blocks.yellowStone;
            }

            if(floor == Blocks.redStone && noise(x + 78 - y, y, 4, 0.73f, 19f, 1f) > 0.63f){
                floor = Blocks.denseRedStone;
            }
        });

        inverseFloodFill(tiles.getn(spawnX, spawnY));

        //TODO veins, blend after inverse flood fill?
        blend(Blocks.redStoneWall, Blocks.denseRedStone, 4);

        //make sure enemies have room
        erase(endX, endY, 6);

        //TODO enemies get stuck on 1x1 passages.

        tiles.getn(endX, endY).setOverlay(Blocks.spawn);

        //ores
        pass((x, y) -> {

            if(block != Blocks.air){
                if(nearAir(x, y)){
                    if(block == Blocks.carbonWall && noise(x + 78, y, 4, 0.7f, 33f, 1f) > 0.52f){
                        block = Blocks.graphiticWall;
                    }else if(block != Blocks.carbonWall && noise(x + 782, y, 4, 0.8f, 38f, 1f) > 0.665f){
                        ore = Blocks.wallOreBeryl;
                    }

                }
            }else if(!nearWall(x, y)){

                if(noise(x + 150, y + x*2 + 100, 4, 0.8f, 55f, 1f) > 0.76f/* && floor == Blocks.yellowStone*/){
                    ore = Blocks.oreTungsten;
                }

                //TODO design ore generation so it doesn't overlap
                if(noise(x + 999, y + 600 - x, 4, 0.63f, 45f, 1f) < 0.29f && floor == Blocks.crystallineStone){
                    ore = Blocks.oreCrystalThorium;
                }

            }

            if(noise(x + 999, y + 600 - x, 5, 0.8f, 45f, 1f) < 0.44f && floor == Blocks.crystallineStone){
                floor = Blocks.crystalFloor;
            }

            if(block == Blocks.air && (floor == Blocks.crystallineStone || floor == Blocks.crystalFloor) && rand.chance(0.09) && nearWall(x, y)
                && !near(x, y, 4, Blocks.crystalCluster) && !near(x, y, 4, Blocks.vibrantCrystalCluster)){
                block = floor == Blocks.crystalFloor ? Blocks.vibrantCrystalCluster : Blocks.crystalCluster;
                ore = Blocks.air;
            }

            if(block == Blocks.arkyicWall && rand.chance(0.23) && nearAir(x, y) && !near(x, y, 3, Blocks.crystalOrbs)){
                block = Blocks.crystalOrbs;
                ore = Blocks.air;
            }

            //TODO test, different placement
            //TODO this biome should have more blocks in general
            if(block == Blocks.regolithWall && rand.chance(0.3) && nearAir(x, y) && !near(x, y, 3, Blocks.crystalBlocks)){
                block = Blocks.crystalBlocks;
                ore = Blocks.air;
            }

            //this is annoying as blocks under it can't be seen
            //if(block == Blocks.air && floor == Blocks.regolith && rand.chance(0.004) && !near(x, y, 3, Blocks.crystalBlocks)){
            //    block = Blocks.crystalBlocks;
            //}
        });

        //remove props near ores, they're too annoying
        pass((x, y) -> {
            if(ore.asFloor().wallOre || block.itemDrop != null || (block == Blocks.air && ore != Blocks.air)){
                removeWall(x, y, 3, b -> b instanceof TallBlock);
            }
        });

        trimDark();

        int minVents = rand.random(5, 8);
        int ventCount = 0;

        //TODO vents everywhere! rhyolite patches
        //vents
        outer:
        for(Tile tile : tiles){
            var floor = tile.floor();
            if((floor == Blocks.rhyolite || floor == Blocks.roughRhyolite) && rand.chance(0.002)){
                int radius = 2;
                for(int x = -radius; x <= radius; x++){
                    for(int y = -radius; y <= radius; y++){
                        Tile other = tiles.get(x + tile.x, y + tile.y);
                        if(other == null || (other.floor() != Blocks.rhyolite && other.floor() != Blocks.roughRhyolite) || other.block().solid){
                            continue outer;
                        }
                    }
                }

                ventCount ++;
                for(var pos : SteamVent.offsets){
                    Tile other = tiles.get(pos.x + tile.x + 1, pos.y + tile.y + 1);
                    other.setFloor(Blocks.steamVent.asFloor());
                }
            }
        }

        int iterations = 0;
        int maxIterations = 5;

        //try to add additional vents, but only several times to prevent infinite loops in bad maps
        while(ventCount < minVents && iterations++ < maxIterations){
            outer:
            for(Tile tile : tiles){
                if(rand.chance(0.00018)){
                    int radius = 1;
                    for(int x = -radius; x <= radius; x++){
                        for(int y = -radius; y <= radius; y++){
                            Tile other = tiles.get(x + tile.x, y + tile.y);
                            //skip solids / other vents / arkycite / slag
                            if(other == null || other.block().solid || other.floor() == Blocks.steamVent || other.floor() == Blocks.slag || other.floor() == Blocks.arkyciteFloor){
                                continue outer;
                            }
                        }
                    }

                    ventCount ++;
                    for(var pos : SteamVent.offsets){
                        Tile other = tiles.get(pos.x + tile.x + 1, pos.y + tile.y + 1);
                        other.setFloor(Blocks.steamVent.asFloor());
                    }

                    //"circle" for blending
                    //TODO should it replace akrycite? slag?
                    int crad = rand.random(6, 14), crad2 = crad * crad;
                    for(int cx = -crad; cx <= crad; cx++){
                        for(int cy = -crad; cy <= crad; cy++){
                            int rx = cx + tile.x, ry = cy + tile.y;
                            //skew circle Y
                            float rcy = cy + cx*0.9f;
                            if(cx*cx + rcy*rcy <= crad2 - noise(rx, ry + rx*2f, 2, 0.7f, 8f, crad2 * 1.1f)){
                                Tile dest = tiles.get(rx, ry);
                                if(dest != null && dest.floor() != Blocks.steamVent && dest.floor() != Blocks.roughRhyolite && dest.floor() != Blocks.arkyciteFloor && dest.floor() != Blocks.slag){
                                    dest.setFloor(rand.chance(0.08) ? Blocks.rhyoliteCrater.asFloor() : Blocks.rhyolite.asFloor());
                                    if(dest.block().isStatic()){
                                        dest.setBlock(Blocks.rhyoliteWall);
                                    }
                                }
                            }
                        }
                    }

                }
            }
        }

        for(Tile tile : tiles){
            if(tile.overlay().needsSurface && !tile.floor().hasSurface()){
                tile.setOverlay(Blocks.air);
            }
        }

        decoration(0.017f);

        //it is very hot
        state.rules.attributes.set(Attribute.heat, 0.8f);
        state.rules.environment = sector.planet.defaultEnv;
        Schematics.placeLaunchLoadout(spawnX, spawnY);

        //all sectors are wave sectors
        state.rules.waves = true;
        state.rules.showSpawns = true;
    }
}
