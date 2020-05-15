package com.github.alexthe666.iceandfire.entity;

import com.github.alexthe666.iceandfire.IceAndFire;
import com.github.alexthe666.iceandfire.entity.ai.*;
import com.google.common.base.Predicate;
import net.ilexiconn.llibrary.server.animation.Animation;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.*;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.init.MobEffects;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.pathfinding.PathNavigateClimber;
import net.minecraft.pathfinding.PathNavigateFlying;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.storage.loot.LootTableList;
import net.minecraftforge.fml.common.registry.VillagerRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.Random;

public class EntityMyrmexRoyal extends EntityMyrmexBase {

    public static final Animation ANIMATION_BITE = Animation.create(15);
    public static final Animation ANIMATION_STING = Animation.create(15);
    public static final ResourceLocation DESERT_LOOT = LootTableList.register(new ResourceLocation("iceandfire", "myrmex_royal_desert"));
    public static final ResourceLocation JUNGLE_LOOT = LootTableList.register(new ResourceLocation("iceandfire", "myrmex_royal_jungle"));
    private static final ResourceLocation TEXTURE_DESERT = new ResourceLocation("iceandfire:textures/models/myrmex/myrmex_desert_royal.png");
    private static final ResourceLocation TEXTURE_JUNGLE = new ResourceLocation("iceandfire:textures/models/myrmex/myrmex_jungle_royal.png");
    private static final DataParameter<Boolean> FLYING = EntityDataManager.createKey(EntityMyrmexRoyal.class, DataSerializers.BOOLEAN);
    public int releaseTicks = 0;
    public int daylightTicks = 0;
    public float flyProgress;
    public EntityMyrmexRoyal mate;
    private int hiveTicks = 0;
    private int breedingTicks = 0;
    private boolean isFlying;
    private boolean isLandNavigator;
    private boolean isMating = false;

    public EntityMyrmexRoyal(World worldIn) {
        super(worldIn);
        this.switchNavigator(true);
    }

    public static BlockPos getPositionRelativetoGround(Entity entity, World world, double x, double z, Random rand) {
        BlockPos pos = new BlockPos(x, entity.getPosY(), z);
        for (int yDown = 0; yDown < 10; yDown++) {
            if (!world.isAirBlock(pos.down(yDown))) {
                return pos.up(yDown);
            }
        }
        return pos;
    }

    @Nullable
    protected ResourceLocation getLootTable() {
        return isJungle() ? JUNGLE_LOOT : DESERT_LOOT;
    }

    protected int getExperiencePoints(PlayerEntity player) {
        return 10;
    }

    @Override
    protected void registerData() {
        super.registerData();
        this.dataManager.register(FLYING, Boolean.valueOf(false));
    }

    protected void switchNavigator(boolean onLand) {
        if (onLand) {
            this.moveController = new EntityMoveHelper(this);
            this.navigator = new PathNavigateClimber(this, world);
            this.isLandNavigator = true;
        } else {
            this.moveController = new EntityMyrmexRoyal.FlyMoveHelper(this);
            this.navigator = new PathNavigateFlying(this, world);
            this.isLandNavigator = false;
        }
    }

    public boolean isFlying() {
        if (world.isRemote) {
            return this.isFlying = this.dataManager.get(FLYING).booleanValue();
        }
        return isFlying;
    }

    public void setFlying(boolean flying) {
        this.dataManager.set(FLYING, flying);
        if (!world.isRemote) {
            this.isFlying = flying;
        }
    }

    @Override
    public void writeEntityToNBT(CompoundNBT tag) {
        super.writeEntityToNBT(tag);
        tag.putInt("HiveTicks", hiveTicks);
        tag.putInt("ReleaseTicks", releaseTicks);
        tag.setBoolean("Flying", this.isFlying());
    }

    @Override
    public void readEntityFromNBT(CompoundNBT tag) {
        super.readEntityFromNBT(tag);
        this.hiveTicks = tag.getInt("HiveTicks");
        this.releaseTicks = tag.getInt("ReleaseTicks");
        this.setFlying(tag.getBoolean("Flying"));
    }

    public void onLivingUpdate() {
        super.onLivingUpdate();
        boolean flying = this.isFlying() && !this.onGround;
        if (flying && flyProgress < 20.0F) {
            flyProgress += 1F;
        } else if (!flying && flyProgress > 0.0F) {
            flyProgress -= 1F;
        }
        if (flying) {
            double up = isInWater() ? 0.16D : 0.08D;
            this.motionY += up;
        }
        if (flying && this.isLandNavigator) {
            switchNavigator(false);
        }
        if (!flying && !this.isLandNavigator) {
            switchNavigator(true);
        }
        if (this.canSeeSky()) {
            this.daylightTicks++;
        } else {
            this.daylightTicks = 0;
        }
        if (flying && this.canSeeSky() && this.isBreedingSeason()) {
            this.releaseTicks++;
        }
        if (!flying && this.canSeeSky() && daylightTicks > 300 && this.isBreedingSeason() && this.getAttackTarget() == null && this.canMove() && this.onGround && !isMating) {
            this.setFlying(true);
            this.motionY += 0.42D;
        }
        if (this.getGrowthStage() >= 2) {
            hiveTicks++;
        }
        if (this.getAnimation() == ANIMATION_BITE && this.getAttackTarget() != null && this.getAnimationTick() == 6) {
            this.playBiteSound();
            if (this.getAttackBounds().intersects(this.getAttackTarget().getBoundingBox())) {
                this.getAttackTarget().attackEntityFrom(DamageSource.causeMobDamage(this), ((int) this.getAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getValue()));
            }
        }
        if (this.getAnimation() == ANIMATION_STING && this.getAttackTarget() != null && this.getAnimationTick() == 6) {
            this.playStingSound();
            if (this.getAttackBounds().intersects(this.getAttackTarget().getBoundingBox())) {
                this.getAttackTarget().attackEntityFrom(DamageSource.causeMobDamage(this), ((int) this.getAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getValue() * 2));
                this.getAttackTarget().addPotionEffect(new PotionEffect(MobEffects.POISON, 70, 1));
            }
        }
        if (this.mate != null) {
            this.world.setEntityState(this, (byte) 77);
            if (this.getDistance(this.mate) < 10) {
                this.setFlying(false);
                this.mate.setFlying(false);
                isMating = true;
                if (this.onGround && this.mate.onGround) {
                    breedingTicks++;
                    if (breedingTicks > 100) {
                        if (this.isEntityAlive()) {
                            this.mate.setDead();
                            this.setDead();
                            EntityMyrmexQueen queen = new EntityMyrmexQueen(this.world);
                            queen.copyLocationAndAnglesFrom(this);
                            queen.setJungleVariant(this.isJungle());
                            queen.setMadeHome(false);
                            if (!world.isRemote) {
                                world.spawnEntity(queen);
                            }
                        }
                        isMating = false;
                    }
                }
            }
            this.mate.mate = this;
            if (!this.mate.isEntityAlive()) {
                this.mate.mate = null;
                this.mate = null;
            }
        }
    }

    protected double attackDistance() {
        return 8;
    }

    protected void initEntityAI() {
        this.goalSelector.addGoal(0, new EntityAISwimming(this));
        this.goalSelector.addGoal(0, new MyrmexAITradePlayer(this));
        this.goalSelector.addGoal(0, new MyrmexAILookAtTradePlayer(this));
        this.goalSelector.addGoal(0, new MyrmexAIMoveToMate(this, 1.0D));
        this.goalSelector.addGoal(1, new AIFlyAtTarget());
        this.goalSelector.addGoal(2, new AIFlyRandom());
        this.goalSelector.addGoal(3, new EntityAIAttackMelee(this, 1.0D, true));
        this.goalSelector.addGoal(4, new MyrmexAILeaveHive(this, 1.0D));
        this.goalSelector.addGoal(4, new MyrmexAIReEnterHive(this, 1.0D));
        this.goalSelector.addGoal(5, new MyrmexAIMoveThroughHive(this, 1.0D));
        this.goalSelector.addGoal(5, new MyrmexAIWanderHiveCenter(this, 1.0D));
        this.goalSelector.addGoal(6, new MyrmexAIWander(this, 1D));
        this.goalSelector.addGoal(7, new EntityAIWatchClosest(this, PlayerEntity.class, 6.0F));
        this.goalSelector.addGoal(7, new EntityAILookIdle(this));
        this.targetSelector.addGoal(1, new MyrmexAIDefendHive(this));
        this.targetSelector.addGoal(2, new MyrmexAIFindMate(this));
        this.targetSelector.addGoal(3, new EntityAIHurtByTarget(this, false));
        this.targetSelector.addGoal(4, new MyrmexAIAttackPlayers(this));
        this.targetSelector.addGoal(4, new EntityAINearestAttackableTarget(this, LivingEntity.class, 10, true, true, new Predicate<LivingEntity>() {
            public boolean apply(@Nullable LivingEntity entity) {
                if (entity instanceof EntityMyrmexBase && EntityMyrmexRoyal.this.isBreedingSeason() || entity instanceof EntityMyrmexRoyal) {
                    return false;
                }
                return entity != null && !IMob.VISIBLE_MOB_SELECTOR.apply(entity) && !EntityMyrmexBase.haveSameHive(EntityMyrmexRoyal.this, entity) && DragonUtils.isAlive(entity);
            }
        }));

    }

    public boolean canMateWith(EntityAnimal otherAnimal) {
        if (otherAnimal == this || otherAnimal == null) {
            return false;
        } else if (otherAnimal.getClass() != this.getClass()) {
            return false;
        } else {
            if (otherAnimal instanceof EntityMyrmexBase) {
                if (((EntityMyrmexBase) otherAnimal).getHive() != null && this.getHive() != null) {
                    return !this.getHive().equals(((EntityMyrmexBase) otherAnimal).getHive());
                } else {
                    return true;
                }
            }
            return false;
        }
    }

    public VillagerRegistry.VillagerProfession getProfessionForge() {
        return this.isJungle() ? IafVillagerRegistry.INSTANCE.jungleMyrmexRoyal : IafVillagerRegistry.INSTANCE.desertMyrmexRoyal;
    }

    public boolean shouldMoveThroughHive() {
        return false;
    }

    protected void registerAttributes() {
        super.registerAttributes();
        this.getAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.3D);
        this.getAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(IafConfig.myrmexBaseAttackStrength * 2D);
        this.getAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(50);
        this.getAttribute(SharedMonsterAttributes.ARMOR).setBaseValue(9.0D);
    }

    @Override
    public ResourceLocation getAdultTexture() {
        return isJungle() ? TEXTURE_JUNGLE : TEXTURE_DESERT;
    }

    @Override
    public float getModelScale() {
        return 1.25F;
    }

    @Override
    public int getCasteImportance() {
        return 2;
    }

    public boolean shouldLeaveHive() {
        return isBreedingSeason();
    }

    public boolean shouldEnterHive() {
        return !isBreedingSeason();
    }

    @Override
    public boolean attackEntityAsMob(Entity entityIn) {
        if (this.getGrowthStage() < 2) {
            return false;
        }
        if (this.getAnimation() != ANIMATION_STING && this.getAnimation() != ANIMATION_BITE) {
            this.setAnimation(this.getRNG().nextBoolean() ? ANIMATION_STING : ANIMATION_BITE);
            return true;
        }
        return false;
    }

    @Override
    public Animation[] getAnimations() {
        return new Animation[]{ANIMATION_PUPA_WIGGLE, ANIMATION_BITE, ANIMATION_STING};
    }

    public boolean isBreedingSeason() {
        return this.getGrowthStage() >= 2 && hiveTicks > 4000 && (this.getHive() == null || this.getHive().reproduces);
    }

    @OnlyIn(Dist.CLIENT)
    public void handleStatusUpdate(byte id) {
        if (id == 76) {
            this.playEffect(20);
        } else if (id == 77) {
            this.playEffect(7);
        } else {
            super.handleStatusUpdate(id);
        }
    }

    protected void playEffect(int hearts) {
        ParticleTypes enumparticletypes = ParticleTypes.HEART;

        for (int i = 0; i < hearts; ++i) {
            double d0 = this.rand.nextGaussian() * 0.02D;
            double d1 = this.rand.nextGaussian() * 0.02D;
            double d2 = this.rand.nextGaussian() * 0.02D;
            this.world.spawnParticle(enumparticletypes, this.getPosX() + (double) (this.rand.nextFloat() * this.getWidth() * 2.0F) - (double) this.getWidth(), this.getPosY() + 0.5D + (double) (this.rand.nextFloat() * this.height), this.getPosZ() + (double) (this.rand.nextFloat() * this.getWidth() * 2.0F) - (double) this.getWidth(), d0, d1, d2);
        }
    }

    protected void updateFallState(double y, boolean onGroundIn, BlockState state, BlockPos pos) {
        if (!this.isInWater()) {
            this.handleWaterMovement();
        }

        if (onGroundIn) {
            if (this.fallDistance > 0.0F) {
                state.getBlock().onFallenUpon(this.world, pos, this, this.fallDistance);
            }

            this.fallDistance = 0.0F;
        } else if (y < 0.0D) {
            this.fallDistance = (float) ((double) this.fallDistance - y);
        }
    }

    class FlyMoveHelper extends EntityMoveHelper {
        public FlyMoveHelper(EntityMyrmexRoyal pixie) {
            super(pixie);
            this.speed = 1.75F;
        }

        public void onUpdateMoveHelper() {
            if (this.action == EntityMoveHelper.Action.MOVE_TO) {
                if (EntityMyrmexRoyal.this.collidedHorizontally) {
                    EntityMyrmexRoyal.this.rotationYaw += 180.0F;
                    this.speed = 0.1F;
                    BlockPos target = EntityMyrmexRoyal.getPositionRelativetoGround(EntityMyrmexRoyal.this, EntityMyrmexRoyal.this.world, EntityMyrmexRoyal.this.getPosX() + EntityMyrmexRoyal.this.rand.nextInt(15) - 7, EntityMyrmexRoyal.this.getPosZ() + EntityMyrmexRoyal.this.rand.nextInt(15) - 7, EntityMyrmexRoyal.this.rand);
                    this.getPosX() = target.getX();
                    this.getPosY() = target.getY();
                    this.getPosZ() = target.getZ();
                }
                double d0 = this.getPosX() - EntityMyrmexRoyal.this.getPosX();
                double d1 = this.getPosY() - EntityMyrmexRoyal.this.getPosY();
                double d2 = this.getPosZ() - EntityMyrmexRoyal.this.getPosZ();
                double d3 = d0 * d0 + d1 * d1 + d2 * d2;
                d3 = (double) MathHelper.sqrt(d3);

                if (d3 < EntityMyrmexRoyal.this.getBoundingBox().getAverageEdgeLength()) {
                    this.action = EntityMoveHelper.Action.WAIT;
                    EntityMyrmexRoyal.this.motionX *= 0.5D;
                    EntityMyrmexRoyal.this.motionY *= 0.5D;
                    EntityMyrmexRoyal.this.motionZ *= 0.5D;
                } else {
                    EntityMyrmexRoyal.this.motionX += d0 / d3 * 0.1D * this.speed;
                    EntityMyrmexRoyal.this.motionY += d1 / d3 * 0.1D * this.speed;
                    EntityMyrmexRoyal.this.motionZ += d2 / d3 * 0.1D * this.speed;

                    if (EntityMyrmexRoyal.this.getAttackTarget() == null) {
                        EntityMyrmexRoyal.this.rotationYaw = -((float) MathHelper.atan2(EntityMyrmexRoyal.this.motionX, EntityMyrmexRoyal.this.motionZ)) * (180F / (float) Math.PI);
                        EntityMyrmexRoyal.this.renderYawOffset = EntityMyrmexRoyal.this.rotationYaw;
                    } else {
                        double d4 = EntityMyrmexRoyal.this.getAttackTarget().getPosX() - EntityMyrmexRoyal.this.getPosX();
                        double d5 = EntityMyrmexRoyal.this.getAttackTarget().getPosZ() - EntityMyrmexRoyal.this.getPosZ();
                        EntityMyrmexRoyal.this.rotationYaw = -((float) MathHelper.atan2(d4, d5)) * (180F / (float) Math.PI);
                        EntityMyrmexRoyal.this.renderYawOffset = EntityMyrmexRoyal.this.rotationYaw;
                    }
                }
            }
        }
    }

    class AIFlyRandom extends Goal {
        BlockPos target;

        public AIFlyRandom() {
            this.setMutexBits(1);
        }

        public boolean shouldExecute() {
            if (EntityMyrmexRoyal.this.isFlying() && EntityMyrmexRoyal.this.getAttackTarget() == null) {
                if (EntityMyrmexRoyal.this instanceof EntityMyrmexSwarmer && ((EntityMyrmexSwarmer) EntityMyrmexRoyal.this).getSummoner() != null) {
                    Entity summon = ((EntityMyrmexSwarmer) EntityMyrmexRoyal.this).getSummoner();
                    target = EntityMyrmexRoyal.getPositionRelativetoGround(EntityMyrmexRoyal.this, EntityMyrmexRoyal.this.world, summon.getPosX() + EntityMyrmexRoyal.this.rand.nextInt(10) - 5, summon.getPosZ() + EntityMyrmexRoyal.this.rand.nextInt(10) - 5, EntityMyrmexRoyal.this.rand);
                } else {
                    target = EntityMyrmexRoyal.getPositionRelativetoGround(EntityMyrmexRoyal.this, EntityMyrmexRoyal.this.world, EntityMyrmexRoyal.this.getPosX() + EntityMyrmexRoyal.this.rand.nextInt(30) - 15, EntityMyrmexRoyal.this.getPosZ() + EntityMyrmexRoyal.this.rand.nextInt(30) - 15, EntityMyrmexRoyal.this.rand);
                }
                return isDirectPathBetweenPoints(EntityMyrmexRoyal.this.getPosition(), target) && !EntityMyrmexRoyal.this.getMoveHelper().isUpdating() && EntityMyrmexRoyal.this.rand.nextInt(2) == 0;
            } else {
                return false;
            }
        }

        protected boolean isDirectPathBetweenPoints(BlockPos posVec31, BlockPos posVec32) {
            RayTraceResult raytraceresult = EntityMyrmexRoyal.this.world.rayTraceBlocks(new Vec3d(posVec31.getX() + 0.5D, posVec31.getY() + 0.5D, posVec31.getZ() + 0.5D), new Vec3d(posVec32.getX() + 0.5D, posVec32.getY() + (double) EntityMyrmexRoyal.this.height * 0.5D, posVec32.getZ() + 0.5D), false, true, false);
            return raytraceresult == null || raytraceresult.typeOfHit == RayTraceResult.Type.MISS;
        }

        public boolean shouldContinueExecuting() {
            return false;
        }

        public void updateTask() {
            if (!isDirectPathBetweenPoints(EntityMyrmexRoyal.this.getPosition(), target)) {
                if (EntityMyrmexRoyal.this instanceof EntityMyrmexSwarmer && ((EntityMyrmexSwarmer) EntityMyrmexRoyal.this).getSummoner() != null) {
                    Entity summon = ((EntityMyrmexSwarmer) EntityMyrmexRoyal.this).getSummoner();
                    target = EntityMyrmexRoyal.getPositionRelativetoGround(EntityMyrmexRoyal.this, EntityMyrmexRoyal.this.world, summon.getPosX() + EntityMyrmexRoyal.this.rand.nextInt(10) - 5, summon.getPosZ() + EntityMyrmexRoyal.this.rand.nextInt(10) - 5, EntityMyrmexRoyal.this.rand);
                } else {
                    target = EntityMyrmexRoyal.getPositionRelativetoGround(EntityMyrmexRoyal.this, EntityMyrmexRoyal.this.world, EntityMyrmexRoyal.this.getPosX() + EntityMyrmexRoyal.this.rand.nextInt(30) - 15, EntityMyrmexRoyal.this.getPosZ() + EntityMyrmexRoyal.this.rand.nextInt(30) - 15, EntityMyrmexRoyal.this.rand);
                }
            }
            if (EntityMyrmexRoyal.this.world.isAirBlock(target)) {
                EntityMyrmexRoyal.this.moveController.setMoveTo((double) target.getX() + 0.5D, (double) target.getY() + 0.5D, (double) target.getZ() + 0.5D, 0.25D);
                if (EntityMyrmexRoyal.this.getAttackTarget() == null) {
                    EntityMyrmexRoyal.this.getLookController().setLookPosition((double) target.getX() + 0.5D, (double) target.getY() + 0.5D, (double) target.getZ() + 0.5D, 180.0F, 20.0F);

                }
            }
        }
    }

    class AIFlyAtTarget extends Goal {
        public AIFlyAtTarget() {
            this.setMutexBits(0);
        }

        public boolean shouldExecute() {
            if (EntityMyrmexRoyal.this.getAttackTarget() != null && !EntityMyrmexRoyal.this.getMoveHelper().isUpdating() && EntityMyrmexRoyal.this.rand.nextInt(7) == 0) {
                return EntityMyrmexRoyal.this.getDistanceSq(EntityMyrmexRoyal.this.getAttackTarget()) > 4.0D;
            } else {
                return false;
            }
        }

        public boolean shouldContinueExecuting() {
            return EntityMyrmexRoyal.this.getMoveHelper().isUpdating() && EntityMyrmexRoyal.this.getAttackTarget() != null && EntityMyrmexRoyal.this.getAttackTarget().isEntityAlive();
        }

        public void startExecuting() {
            LivingEntity LivingEntity = EntityMyrmexRoyal.this.getAttackTarget();
            Vec3d vec3d = LivingEntity.getPositionEyes(1.0F);
            EntityMyrmexRoyal.this.moveController.setMoveTo(vec3d.x, vec3d.y, vec3d.z, 1.0D);
        }

        public void resetTask() {

        }

        public void updateTask() {
            LivingEntity LivingEntity = EntityMyrmexRoyal.this.getAttackTarget();
            if(LivingEntity != null){
                if (EntityMyrmexRoyal.this.getBoundingBox().intersects(LivingEntity.getBoundingBox())) {
                    EntityMyrmexRoyal.this.attackEntityAsMob(LivingEntity);
                } else {
                    double d0 = EntityMyrmexRoyal.this.getDistanceSq(LivingEntity);

                    if (d0 < 9.0D) {
                        Vec3d vec3d = LivingEntity.getPositionEyes(1.0F);
                        EntityMyrmexRoyal.this.moveController.setMoveTo(vec3d.x, vec3d.y, vec3d.z, 1.0D);
                    }
                }
            }

        }
    }
}
