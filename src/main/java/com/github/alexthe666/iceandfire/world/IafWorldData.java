package com.github.alexthe666.iceandfire.world;

import com.github.alexthe666.iceandfire.IceAndFire;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.DimensionSavedDataManager;
import net.minecraft.world.storage.WorldSavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class IafWorldData extends WorldSavedData {

    private static final String IDENTIFIER = "iceandfire_general";
    protected BlockPos lastGeneratedDangerousStructure = BlockPos.ZERO;
    private World world;
    private int tickCounter;

    public IafWorldData() {
        super(IDENTIFIER);
    }

    public IafWorldData(World world) {
        super(IDENTIFIER);
        this.world = world;
        this.markDirty();
    }

    public static IafWorldData get(World world) {
        if (world instanceof ServerWorld) {
            ServerWorld overworld = world.getServer().getWorld(world.func_234923_W_());

            DimensionSavedDataManager storage = overworld.getSavedData();
            IafWorldData data = storage.getOrCreate(IafWorldData::new, IDENTIFIER);
            if (data != null) {
                data.world = world;
                data.markDirty();
            }
            return data;
        }
        return null;
    }

    public void setLastGeneratedDangerousStructure( BlockPos pos) {
        lastGeneratedDangerousStructure = pos;
        this.markDirty();
    }


    public BlockPos getLastGeneratedDangerousStructure() {
        return lastGeneratedDangerousStructure;
    }


    public void tick() {
        ++this.tickCounter;
    }

    public void read(CompoundNBT nbt) {
        this.tickCounter = nbt.getInt("Tick");
        lastGeneratedDangerousStructure = new BlockPos(nbt.getInt("LastX"), nbt.getInt("LastY"), nbt.getInt("LastZ"));

    }

    public CompoundNBT write(CompoundNBT compound) {
        compound.putInt("Tick", this.tickCounter);
        compound.putInt("LastX", lastGeneratedDangerousStructure.getX());
        compound.putInt("LastY", lastGeneratedDangerousStructure.getY());
        compound.putInt("LastZ", lastGeneratedDangerousStructure.getZ());
        return compound;
    }
}