package com.hbm.entity.projectile;

import com.hbm.api.entity.IThrowable;
import com.hbm.lib.Library;
import com.hbm.lib.internal.MethodHandleHelper;
import com.hbm.util.TrackerUtil;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.init.Blocks;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.event.ForgeEventFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.List;

/**
 * Near-identical copy of EntityThrowable but deobfuscated & untangled
 *
 * @author hbm
 */
public abstract class EntityThrowableNT extends EntityThrowable implements IThrowable {
    private static final DataParameter<Byte> STUCK_IN = EntityDataManager.createKey(EntityThrowableNT.class, DataSerializers.BYTE);
    private static final MethodHandle ENTITY_ON_UPDATE = MethodHandleHelper.findSpecial(Entity.class, EntityThrowableNT.class, "onUpdate", "func_70071_h_", MethodType.methodType(void.class));

    public EntityThrowableNT(World worldIn) {
        super(worldIn);
    }

    public EntityThrowableNT(World worldIn, double x, double y, double z) {
        super(worldIn, x, y, z);
    }

    public EntityThrowableNT(World worldIn, EntityLivingBase throwerIn) {
        super(worldIn);
        thrower = throwerIn;

        setLocationAndAngles(throwerIn.posX, throwerIn.posY + (double) throwerIn.getEyeHeight(), throwerIn.posZ, throwerIn.rotationYaw, throwerIn.rotationPitch);

        double yawRad = (double) rotationYaw * Library.DEG_TO_RAD;
        double pitchRad = (double) rotationPitch * Library.DEG_TO_RAD;
        posX -= Math.cos(yawRad) * 0.16D;
        posY -= 0.1D;
        posZ -= Math.sin(yawRad) * 0.16D;
        setPosition(posX, posY, posZ);

        double dir = 0.4D;

        motionX = -Math.sin(yawRad) * Math.cos(pitchRad) * dir;
        motionZ = Math.cos(yawRad) * Math.cos(pitchRad) * dir;

        double pitchWithThrow = (rotationPitch + throwAngle()) * Library.DEG_TO_RAD;
        motionY = -Math.sin(pitchWithThrow) * dir;

        shoot(motionX, motionY, motionZ, throwForce(), 1.0F);
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataManager.register(STUCK_IN, (byte) 0);
    }

    public int getStuckIn() {
        return dataManager.get(STUCK_IN);
    }

    public void setStuckIn(int side) {
        dataManager.set(STUCK_IN, (byte) side);
    }

    protected float throwForce() {
        return 1.5F;
    }

    protected double headingForceMult() {
        return 0.0075D;
    }

    protected float throwAngle() {
        return 0.0F;
    }

    protected double motionMult() {
        return 1.0D;
    }

    public boolean doesImpactEntities() {
        return true;
    }

    public boolean doesPenetrate() {
        return false;
    }

    public boolean isSpectral() {
        return false;
    }

    public int selfDamageDelay() {
        return 5;
    }

    @Override
    public void shoot(double x, double y, double z, float velocity, float inaccuracy) {
        double lenSq = x * x + y * y + z * z;
        if (lenSq < 1.0e-24D) return;

        double invLen = 1.0D / Math.sqrt(lenSq);
        x *= invLen;
        y *= invLen;
        z *= invLen;

        double hf = headingForceMult() * (double) inaccuracy;
        x += rand.nextGaussian() * hf;
        y += rand.nextGaussian() * hf;
        z += rand.nextGaussian() * hf;

        x *= (double) velocity;
        y *= (double) velocity;
        z *= (double) velocity;

        motionX = x;
        motionY = y;
        motionZ = z;

        double hyp = Math.sqrt(x * x + z * z);
        rotationYaw = (float) (Math.atan2(x, z) * 180.0D / Math.PI);
        rotationPitch = (float) (Math.atan2(y, hyp) * 180.0D / Math.PI);

        prevRotationYaw = rotationYaw;
        prevRotationPitch = rotationPitch;

        ticksInGround = 0;
    }

    @Override
    public void onUpdate() {
        lastTickPosX = posX;
        lastTickPosY = posY;
        lastTickPosZ = posZ;

        try {
            ENTITY_ON_UPDATE.invokeExact(this);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to invoke Entity#onUpdate via MethodHandle", t);
        }

        if (throwableShake > 0) {
            --throwableShake;
        }

        if (inGround) {
            Block current = world.getBlockState(new BlockPos(xTile, yTile, zTile)).getBlock();

            if (current == inTile) {
                ++ticksInGround;

                int despawn = groundDespawn();
                if (despawn > 0 && ticksInGround == despawn) {
                    setDead();
                }
                return;
            }

            inGround = false;
            motionX *= rand.nextFloat() * 0.2F;
            motionY *= rand.nextFloat() * 0.2F;
            motionZ *= rand.nextFloat() * 0.2F;
            ticksInGround = 0;
            ticksInAir = 0;
            setStuckIn(0);
        } else {
            ++ticksInAir;
        }

        double mm = motionMult();
        double mx = motionX * mm;
        double my = motionY * mm;
        double mz = motionZ * mm;

        Vec3d start = new Vec3d(posX, posY, posZ);
        Vec3d end = new Vec3d(posX + mx, posY + my, posZ + mz);

        RayTraceResult hit = null;

        if (!isSpectral()) {
            hit = Library.rayTraceBlocks(world, start, end, false, true, false);
        }

        Vec3d entityEnd = (hit != null) ? hit.hitVec : end;

        if (doesImpactEntities()) {
            EntityLivingBase thrower = getThrower();
            int selfDelay = selfDamageDelay();

            if (doesPenetrate()) {
                AxisAlignedBB query = getEntityBoundingBox().expand(mx, my, mz).grow(1.0D);
                List<Entity> list = world.getEntitiesWithinAABBExcludingEntity(this, query);

                for (int i = 0, n = list.size(); i < n; i++) {
                    Entity e = list.get(i);
                    if (!e.canBeCollidedWith() || !e.isEntityAlive()) continue;
                    if (e == thrower && ticksInAir < selfDelay) continue;

                    AxisAlignedBB aabb = e.getEntityBoundingBox().grow(0.3D);
                    RayTraceResult r = aabb.calculateIntercept(start, entityEnd);
                    if (r == null) continue;

                    if (!world.isRemote) {
                        onImpact(new RayTraceResult(e, r.hitVec));
                        if (!isEntityAlive()) break;
                    }
                }
            } else {
                Entity exclude = (thrower != null && ticksInAir < selfDelay) ? thrower : this;
                RayTraceResult entityHit = Library.rayTraceEntities(world, exclude, start, entityEnd, 0.3D, Entity::isEntityAlive);
                if (entityHit != null) {
                    hit = entityHit;
                }
            }
        }

        if (hit != null) {
            if (hit.typeOfHit == RayTraceResult.Type.BLOCK && world.getBlockState(hit.getBlockPos()).getBlock() == Blocks.PORTAL) {
                setPortal(hit.getBlockPos());
            } else if (!ForgeEventFactory.onProjectileImpact(this, hit)) {
                onImpact(hit);
            }
        }

        posX += mx;
        posY += my;
        posZ += mz;

        double horiz = Math.sqrt(motionX * motionX + motionZ * motionZ);
        rotationYaw = (float) (Math.atan2(motionX, motionZ) * 180.0D / Math.PI);
        rotationPitch = (float) (Math.atan2(motionY, horiz) * 180.0D / Math.PI);
        while (rotationPitch - prevRotationPitch < -180.0F) prevRotationPitch -= 360.0F;
        while (rotationPitch - prevRotationPitch >= 180.0F) prevRotationPitch += 360.0F;
        while (rotationYaw - prevRotationYaw < -180.0F) prevRotationYaw -= 360.0F;
        while (rotationYaw - prevRotationYaw >= 180.0F) prevRotationYaw += 360.0F;

        rotationPitch = prevRotationPitch + (rotationPitch - prevRotationPitch) * 0.2F;
        rotationYaw = prevRotationYaw + (rotationYaw - prevRotationYaw) * 0.2F;

        float drag = getAirDrag();
        float gravity = getGravityVelocity();

        if (isInWater()) {
            for (int j = 0; j < 4; ++j) {
                world.spawnParticle(EnumParticleTypes.WATER_BUBBLE, posX - motionX * 0.25D, posY - motionY * 0.25D, posZ - motionZ * 0.25D, motionX, motionY, motionZ);
            }
            drag = getWaterDrag();
        }

        motionX *= (double) drag;
        motionY *= (double) drag;
        motionZ *= (double) drag;

        if (!hasNoGravity()) {
            motionY -= (double) gravity;
        }

        setPosition(posX, posY, posZ);
    }

    public void getStuck(BlockPos pos, int side) {
        xTile = pos.getX();
        yTile = pos.getY();
        zTile = pos.getZ();
        inTile = world.getBlockState(pos).getBlock();

        inGround = true;
        motionX = 0.0D;
        motionY = 0.0D;
        motionZ = 0.0D;

        setStuckIn(side);
        TrackerUtil.sendTeleport(world, this);
    }

    @Override
    public void setThrower(EntityLivingBase thrower) {
        this.thrower = thrower;
    }

    /* ================================== Additional Getters =====================================*/
    //Use lombok for love of god

    protected float getAirDrag() {
        return 0.99F;
    }

    protected float getWaterDrag() {
        return 0.8F;
    }

    protected int groundDespawn() {
        return 1200;
    }
}
