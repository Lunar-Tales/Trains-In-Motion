package trains.entities;

import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.registry.IEntityAdditionalSpawnData;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.IEntityMultiPart;
import net.minecraft.entity.boss.EntityDragonPart;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import trains.utility.ClientProxy;
import trains.utility.HitboxHandler;
import trains.utility.LampHandler;
import trains.utility.RailUtility;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static trains.utility.RailUtility.rotatePoint;


public class GenericRailTransport extends Entity implements IEntityAdditionalSpawnData, IEntityMultiPart{

    /**
     * <h2>class variables</h2>
     * isLocked is for if the owner has locked it.
     * lamp is used for the lamp, assuming this has one.
     * colors define the skin colors in RGB format.
     * owner defines the UUID of the current owner (usually the player that spawns it)
     * bogie is the list of bogies this has.
     * bogieXYZ is the list of known positions for the bogies, this is mostly used to keep track of where the bogies are supposed to be via NBT.
     * motion is the vector angle that the train is facing, we only initialize it here, so we don't need to initialize it every tick.
     * TODO: isReverse is supposed to be for whether or not the train is in reverse, but we aren't actually using this yet, and it may not even be necessary.
     * hitboxList and hitboxHandler manage the hitboxes the train has, this is mostly dealt with via getParts() and the hitbox functionality.
     *
     * the last part is the generic entity constructor
     */
    public boolean isLocked = false;
    public LampHandler lamp = new LampHandler();
    public int[] colors = new int[]{0,0,0};
    public UUID owner = null;
    public List<EntityBogie> bogie = new ArrayList<EntityBogie>();
    public List<double[]> bogieXYZ = new ArrayList<double[]>();
    protected float[] motion = new float[]{0,0,0};
    public boolean isReverse =false;
    public List<HitboxHandler.multipartHitbox> hitboxList = new ArrayList<HitboxHandler.multipartHitbox>();
    public HitboxHandler hitboxHandler = new HitboxHandler();
    public GenericRailTransport(World world){
        super(world);
    }



    /**
     * <h2>base entity overrides</h2>
     * modify basic entity variables to give different uses/values.
     * entity init runs right before the first tick, but we don't use this.
     * collision and bounding box stuff just return the in-built stuff.
     * getParts returns the list of hitboxes so they can be treated as if they are part of this entity.
     * The positionAndRotation2 override is intended to do the same as the super, except for giving a Y offset on collision, we skip that similar to EntityMinecart.
     */
    public void setOwner(UUID player){owner = player;}
    public UUID getOwnerUUID(){return owner;}
    @Override
    public boolean canBePushed()
    {
        return false;
    }
    @Override
    public void entityInit(){}
    @Override
    public World func_82194_d(){return worldObj;}
    @Override
    public boolean attackEntityFromPart(EntityDragonPart part, DamageSource damageSource, float damage){
        return HitboxHandler.AttackEvent(this,damageSource,damage);
    }
    @Override
    public Entity[] getParts(){
        return hitboxList.toArray(new HitboxHandler.multipartHitbox[hitboxList.size()]);
    }
    @Override
    public AxisAlignedBB getBoundingBox(){return boundingBox;}
    @Override
    public AxisAlignedBB getCollisionBox(Entity collidedWith){return boundingBox;}
    @Override
    public boolean canBeCollidedWith() {return false;}
    @SideOnly(Side.CLIENT)
    public void setPositionAndRotation2(double p_70056_1_, double p_70056_3_, double p_70056_5_, float p_70056_7_, float p_70056_8_, int p_70056_9_) {
        this.setPosition(p_70056_1_, p_70056_3_, p_70056_5_);
        this.setRotation(p_70056_7_, p_70056_8_);
    }


    /**
     * <h3>add bogies</h3>
     * this is called by the bogie on its spawn to add it to this entity's list of bogies, we only do it on client because thats the only side that seems to lose track.
     * @see EntityBogie#readSpawnData(ByteBuf)
     */
    @SideOnly(Side.CLIENT)
    public void addbogies(EntityBogie cart){
        bogie.add(cart);
    }

    /**
     * <h2> Data Syncing and Saving </h2>
     *
     * used for syncing the bogies between client and server, syncing the spawn data with client and server(SpawnData), and saving/loading information from world (NBT)
     * @see IEntityAdditionalSpawnData
     * @see NBTTagCompound
     * the spawn data will make sure that variables that don't uually sync on spawn, like from the item, get synced.
     * the NBT will make sure that variables save to the world so it will be there next time you load the world up.
     */
    @Override
    public void readSpawnData(ByteBuf additionalData) {
        isReverse = additionalData.readBoolean();
        owner = new UUID(additionalData.readLong(), additionalData.readLong());
        //we loop using the offset double length because we expect bogieXYZ to be null.
        for (int i=0; i<getBogieOffsets().size(); i++){
            bogieXYZ.add(new double[]{additionalData.readDouble(), additionalData.readDouble(), additionalData.readDouble()});
        }
    }
    @Override
    public void writeSpawnData(ByteBuf buffer) {
        buffer.writeBoolean(isReverse);
        buffer.writeLong(owner.getMostSignificantBits());
        buffer.writeLong(owner.getLeastSignificantBits());
        for (double[] xyz : bogieXYZ) {
            buffer.writeDouble(xyz[0]);
            buffer.writeDouble(xyz[1]);
            buffer.writeDouble(xyz[2]);
        }
    }
    @Override
    protected void readEntityFromNBT(NBTTagCompound tag) {
        isLocked = tag.getBoolean("extended.islocked");
        lamp.isOn = tag.getBoolean("extended.lamp");
        lamp.X = tag.getInteger("extended.lamp.x");
        lamp.Y = tag.getInteger("extended.lamp.y");
        lamp.Z = tag.getInteger("extended.lamp.z");
        isReverse = tag.getBoolean("extended.isreverse");
        owner = new UUID(tag.getLong("extended.ownerm"),tag.getLong("extended.ownerl"));
        //read through the bogie positions
        NBTTagList bogieTaglList = tag.getTagList("extended.bogies", 10);
        for (int i = 0; i < bogieTaglList.tagCount(); i++) {
            NBTTagCompound nbttagcompound1 = bogieTaglList.getCompoundTagAt(i);
            byte b0 = nbttagcompound1.getByte("bogie");

            if (b0 >= 0) {
                bogieXYZ.add(new double[]{nbttagcompound1.getDouble("bogieindex.a." + i),nbttagcompound1.getDouble("bogieindex.b." + i),nbttagcompound1.getDouble("bogieindex.c." + i)});
            }
        }
    }
    @Override
    protected void writeEntityToNBT(NBTTagCompound tag) {
        tag.setBoolean("extended.islocked", isLocked);
        tag.setBoolean("extended.lamp", lamp.isOn);
        tag.setInteger("extended.lamp.x", lamp.X);
        tag.setInteger("extended.lamp.y", lamp.Y);
        tag.setInteger("extended.lamp.z", lamp.Z);
        tag.setBoolean("extended.isreverse", isReverse);
        tag.setLong("extended.ownerm", owner.getMostSignificantBits());
        tag.setLong("extended.ownerl", owner.getLeastSignificantBits());
        //write the list of bogies
        NBTTagList nbtBogieTaglist = new NBTTagList();
        for (int i = 0; i < bogieXYZ.size(); ++i) {
            if (bogieXYZ.get(i) != null) {
                NBTTagCompound nbttagcompound1 = new NBTTagCompound();
                nbttagcompound1.setByte("bogie", (byte)i);
                nbttagcompound1.setDouble("bogieindex.a." + i,bogieXYZ.get(i)[0]);
                nbttagcompound1.setDouble("bogieindex.b." + i,bogieXYZ.get(i)[1]);
                nbttagcompound1.setDouble("bogieindex.c." + i,bogieXYZ.get(i)[2]);
                nbtBogieTaglist.appendTag(nbttagcompound1);
            }
        }
        tag.setTag("extended.bogies", nbtBogieTaglist);
    }



    /**
     * <h2> on entity update </h2>
     *
     * defines what should be done every tick
     * used for:
     * managing the list of bogies which are used for defining position and rotation, respawning them if they disappear.
     * managing speed, acceleration. and direction.
     * managing rotationYaw and rotationPitch.
     * being sure the train is listed in the main class (for lighting management).
     * @see ClientProxy#onTick(TickEvent.ClientTickEvent)
     *
     * TODO: we need to put back lamp management
     */
    @Override
    public void onUpdate() {
        //if the cart has fallen out of the map, destroy it.
        if (posY < -64.0D){
            worldObj.removeEntity(this);
        }
        //be sure bogies exist
        int xyzSize = bogieXYZ.size()-1;
        if (xyzSize > 0) {
            int bogieSize = bogie.size() - 1;
            //always be sure the bogies exist on client and server.
            if (!worldObj.isRemote && bogieSize < 1) {
                for (double[] pos : bogieXYZ) {
                    //it should never be possible for bogieXYZ to be null unless there is severe server data corruption.
                    EntityBogie spawnBogie = new EntityBogie(worldObj, pos[0], pos[1], pos[2], getEntityId());
                    worldObj.spawnEntityInWorld(spawnBogie);
                    bogie.add(spawnBogie);
                }
                bogieSize = xyzSize;
            }

            /**
             *check if the bogies exist, because they may not yet, and if they do, check if they are actually moving or colliding.
             * no point in processing movement if they aren't moving or if the train hit something.
             * if it is clear however, then we need to add velocity to the bogies based on the current state of the train's speed and fuel, and reposition the train.
             * but either way we have to position the bogies around the train, just to be sure they don't accidentally fly off at some point.
             */
            if (bogieSize>0){

                //handle movement for trains, this will likely need to be different for rollingstock.
                if (this instanceof EntityTrainCore) {
                    motion = rotatePoint(new float[]{((EntityTrainCore)this).processMovement(), 0.0f, 0.0f}, 0.0f, rotationYaw, 0.0f);
                    //move the bogies, unit of motion, blocks per second 1/20
                    for (EntityBogie currentBogie : bogie) {
                        currentBogie.addVelocity(motion[0], currentBogie.motionY, motion[2]);
                        currentBogie.minecartMove();
                    }
                }

                //position this
                setPosition(
                        (bogie.get(bogieSize).posX + bogie.get(0).posX) * 0.5D,
                        (bogie.get(bogieSize).boundingBox.minY + bogie.get(0).boundingBox.minY) * 0.5D,
                        (bogie.get(bogieSize).posZ + bogie.get(0).posZ) * 0.5D);

                setRotation(
                        (float) (Math.atan2(
                                bogie.get(bogieSize).posZ - bogie.get(0).posZ,
                                bogie.get(bogieSize).posX - bogie.get(0).posX)) * RailUtility.degreesF,
                        MathHelper.floor_double(Math.acos(bogie.get(0).posY / bogie.get(bogieSize).posY))
                );
                //align bogies
                for (int i = 0; i < bogie.size(); ) {
                    float[] var = rotatePoint(new float[]{(float) getBogieOffsets().get(i).doubleValue(), 0.0f, 0.0f}, 0.0f, rotationYaw, 0.0f);
                    bogie.get(i).setPosition(var[0] + posX, bogie.get(i).posY, var[2] + posZ);
                    bogieXYZ.set(i, new double[]{bogie.get(i).posX, bogie.get(i).posY, bogie.get(i).posZ});
                    i++;
                }
            }

        }

        //this is just to be sure the client proxy knows this exists so it can manage the lamps.
        if (worldObj.isRemote && xyzSize > 0 && bogieXYZ.get(0)[0] + bogieXYZ.get(0)[1] + bogieXYZ.get(0)[2] != 0.0D) {
            if (!ClientProxy.carts.contains(this)) {
                ClientProxy.carts.add(this);
            }
        }
    }


    /**
     * <h2>Rider offset</h2>
     * this runs every tick to be sure the rider is in the correct position for the
     * TODO get rider offset may need to be a list of positions for rollingstock that can have multiple passengers
     */
    @Override
    public void updateRiderPosition() {
        if (riddenByEntity != null) {
            if (bogie.size()>1) {

                float[] riderOffset = rotatePoint(new float[]{getRiderOffset(),1.5f,0}, rotationPitch, rotationYaw, 0);
                riddenByEntity.setPosition(posX + riderOffset[0], posY + riderOffset[1], posZ + riderOffset[2]);
            } else {
                riddenByEntity.setPosition(posX, posY + 2D, posZ);
            }
        }
    }

    /**
     * <h2>Inherited variables</h2>
     * these functions are overridden by classes that extend this so that way the values can be changed indirectly.
     */
    public List<Double> getBogieOffsets(){return new ArrayList<Double>();}
    public int getType(){return 0;}
    public float getRiderOffset(){return 0;}
    public int[] getHitboxPositions(){return new int[]{-1,0,1};}
    public Item getItem(){return null;}
    public int getInventorySize(){return 3;}
    public String getName(){return "error";}

    //TODO we need to define smoke vector that can be called from the render

}
