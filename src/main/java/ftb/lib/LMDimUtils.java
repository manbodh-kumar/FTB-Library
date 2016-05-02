package ftb.lib;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.DimensionType;
import net.minecraft.world.Teleporter;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

public class LMDimUtils
{
	public static boolean teleportPlayer(Entity entity, EntityDimPos pos)
	{ return pos != null && teleportPlayer(entity, pos.pos, pos.dim.getId()); }
	
	public static boolean teleportPlayer(Entity entity, BlockDimPos pos)
	{ return pos != null && teleportPlayer(entity, pos.toVec(), pos.dim.getId()); }
	
	public static boolean teleportPlayer(Entity entity, Vec3d pos, int dim)
	{
		if(entity == null || pos == null) { return false; }
		
		entity.fallDistance = 0F;
		EntityPlayerMP player = entity instanceof EntityPlayer ? (EntityPlayerMP) entity : null;
		
		if(dim == entity.dimension)
		{
			if(pos.xCoord == entity.posX && pos.yCoord == entity.posY && pos.zCoord == entity.posZ) { return true; }
			
			if(player != null)
			{
				player.playerNetServerHandler.setPlayerLocation(pos.xCoord, pos.yCoord, pos.zCoord, player.rotationYaw, player.rotationPitch);
				return true;
			}
		}
		
		//FTBLib.dev_logger.info("Teleporting " + entity + " from " + new EntityPos(entity) + " to " + new EntityPos(x, y, z, dim));
		int from = entity.dimension;
		float rotationYaw = entity.rotationYaw;
		float rotationPitch = entity.rotationPitch;
		MinecraftServer server = FTBLib.getServer();
		WorldServer fromDim = server.worldServerForDimension(from);
		WorldServer toDim = server.worldServerForDimension(dim);
		
		if(player != null)
		{
			server.getPlayerList().transferPlayerToDimension(player, dim, new TeleporterBlank(toDim));
			if(from == 1 && entity.isEntityAlive())
			{
				// get around vanilla End hacks
				toDim.spawnEntityInWorld(entity);
				toDim.updateEntityWithOptionalForce(entity, false);
			}
		}
		else
		{
			NBTTagCompound tagCompound = new NBTTagCompound();
			entity.writeToNBT(tagCompound);
			Class<? extends Entity> entityClass = entity.getClass();
			fromDim.removeEntity(entity);
			
			try
			{
				Entity newEntity = entityClass.getConstructor(World.class).newInstance(toDim);
				newEntity.readFromNBT(tagCompound);
				newEntity.setLocationAndAngles(pos.xCoord, pos.yCoord, pos.zCoord, rotationYaw, rotationPitch);
				newEntity.forceSpawn = true;
				toDim.spawnEntityInWorld(newEntity);
				newEntity.forceSpawn = false;
			}
			catch(Exception e)
			{
				e.printStackTrace();
			}
		}
		
		entity.fallDistance = 0;
		entity.rotationYaw = rotationYaw;
		entity.rotationPitch = rotationPitch;
		if(player != null) { player.setPositionAndUpdate(pos.xCoord, pos.yCoord, pos.zCoord); }
		else { entity.setPosition(pos.xCoord, pos.yCoord, pos.zCoord); }
		return true;
	}
	
	public static World getWorld(DimensionType dim)
	{ return DimensionManager.getWorld(dim.getId()); }
	
	public static double getMovementFactor(DimensionType dim)
	{
		switch(dim)
		{
			case OVERWORLD:
				return 1D;
			case NETHER:
				return 8D;
			case THE_END:
				return 1D;
			default:
			{
				World w = getWorld(dim);
				return (w == null) ? 1D : w.provider.getMovementFactor();
			}
		}
	}
	
	public static BlockDimPos getSpawnPoint(DimensionType dim)
	{
		World w = getWorld(dim);
		if(w == null) { return null; }
		BlockPos c = w.getSpawnPoint();
		if(c == null) { return null; }
		return new BlockDimPos(c, dim);
	}
	
	public static BlockDimPos getPlayerEntitySpawnPoint(EntityPlayerMP ep, DimensionType dim)
	{
		BlockPos c = ep.getBedLocation(dim.getId());
		if(c == null) { return getSpawnPoint(dim); }
		return new BlockDimPos(c, dim);
	}
	
	private static class TeleporterBlank extends Teleporter
	{
		public TeleporterBlank(WorldServer w)
		{ super(w); }
		
		@Override
		public boolean makePortal(Entity e)
		{ return true; }
		
		@Override
		public boolean placeInExistingPortal(Entity e, float f)
		{ return true; }
		
		@Override
		public void placeInPortal(Entity entity, float f)
		{
			entity.setLocationAndAngles(entity.posX, entity.posY, entity.posZ, entity.rotationPitch, entity.rotationYaw);
			entity.motionX = 0D;
			entity.motionY = 0D;
			entity.motionZ = 0D;
			entity.fallDistance = 0F;
		}
		
		@Override
		public void removeStalePortalLocations(long l)
		{
		}
	}
}