package com.github.alexthe666.iceandfire.entity.ai;

import com.github.alexthe666.iceandfire.entity.EntityMyrmexBase;
import com.github.alexthe666.iceandfire.entity.EntityMyrmexEgg;
import com.github.alexthe666.iceandfire.entity.EntityMyrmexWorker;
import com.google.common.base.Predicate;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.EntityAITarget;
import net.minecraft.entity.ai.goal.TargetGoal;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.util.math.AxisAlignedBB;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MyrmexAIPickupBabies<T extends EntityItem> extends TargetGoal {
    protected final DragonAITargetItems.Sorter theNearestAttackableTargetSorter;
    protected final Predicate<? super LivingEntity> targetEntitySelector;
    public EntityMyrmexWorker myrmex;
    protected LivingEntity targetEntity;

    public MyrmexAIPickupBabies(EntityMyrmexWorker myrmex) {
        super(myrmex, false, false);
        this.theNearestAttackableTargetSorter = new DragonAITargetItems.Sorter(myrmex);
        this.targetEntitySelector = new Predicate<LivingEntity>() {
            @Override
            public boolean apply(@Nullable LivingEntity myrmex) {
                return myrmex != null && (myrmex instanceof EntityMyrmexBase && ((EntityMyrmexBase) myrmex).getGrowthStage() < 2 && !((EntityMyrmexBase) myrmex).isInNursery() || myrmex instanceof EntityMyrmexEgg && !((EntityMyrmexEgg) myrmex).isInNursery());
            }
        };
        this.myrmex = myrmex;
        this.setMutexBits(1);
    }

    @Override
    public boolean shouldExecute() {
        if (!this.myrmex.canMove() || this.myrmex.holdingSomething() || !this.myrmex.getNavigator().noPath() || this.myrmex.shouldEnterHive() || !this.myrmex.keepSearching || this.myrmex.holdingBaby()) {
            return false;
        }
        List<LivingEntity> listBabies = this.taskOwner.world.getEntitiesWithinAABB(LivingEntity.class, this.getTargetableArea(this.getTargetDistance()), this.targetEntitySelector);
        if (listBabies.isEmpty()) {
            return false;
        } else {
            Collections.sort(listBabies, this.theNearestAttackableTargetSorter);
            this.targetEntity = listBabies.get(0);
            return true;
        }
    }

    protected AxisAlignedBB getTargetableArea(double targetDistance) {
        return this.taskOwner.getBoundingBox().grow(targetDistance, 4.0D, targetDistance);
    }

    @Override
    public void startExecuting() {
        this.taskOwner.getNavigator().tryMoveToXYZ(this.targetEntity.getPosX(), this.targetEntity.getPosY(), this.targetEntity.getPosZ(), 1);
        super.startExecuting();
    }

    @Override
    public void updateTask() {
        super.updateTask();
        if (this.targetEntity == null || this.targetEntity != null && this.targetEntity.isDead) {
            this.resetTask();
        }
        if (this.targetEntity != null && !this.targetEntity.isDead && this.taskOwner.getDistanceSq(this.targetEntity) < 2) {
            this.targetEntity.startRiding(this.myrmex);
            resetTask();
        }
    }

    @Override
    public boolean shouldContinueExecuting() {
        return !this.taskOwner.getNavigator().noPath();
    }

    public static class Sorter implements Comparator<Entity> {
        private final Entity theEntity;

        public Sorter(EntityMyrmexBase theEntityIn) {
            this.theEntity = theEntityIn;
        }

        public int compare(Entity p_compare_1_, Entity p_compare_2_) {
            double d0 = this.theEntity.getDistanceSq(p_compare_1_);
            double d1 = this.theEntity.getDistanceSq(p_compare_2_);
            return d0 < d1 ? -1 : (d0 > d1 ? 1 : 0);
        }
    }
}