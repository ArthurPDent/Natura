package mods.natura;

import java.util.Random;

import mantle.lib.TabTools;
import mantle.module.ModuleController;
import mods.natura.common.NContent;
import mods.natura.common.NProxyCommon;
import mods.natura.common.NaturaTab;
import mods.natura.common.PHNatura;
import mods.natura.dimension.NetheriteWorldProvider;
import mods.natura.gui.NGuiHandler;
import mods.natura.plugins.PluginController;
import mods.natura.worldgen.BaseCloudWorldgen;
import mods.natura.worldgen.BaseCropWorldgen;
import mods.natura.worldgen.BaseTreeWorldgen;
import mods.natura.worldgen.retro.TickHandlerWorld;
import mods.natura.worldgen.retro.WorldHandler;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.ai.EntityAITempt;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityChicken;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.entity.passive.EntitySheep;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.player.BonemealEvent;
import net.minecraftforge.event.entity.player.EntityInteractEvent;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.oredict.OreDictionary;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.registry.GameRegistry;

@Mod(modid = "Natura", name = "Natura", version = "2.2.0", acceptedMinecraftVersions = "[1.7.10]", dependencies = "required-after:Mantle")
public class Natura
{
    /* Proxies for sides, used for graphics processing */
    @SidedProxy(clientSide = "mods.natura.client.NProxyClient", serverSide = "mods.natura.common.NProxyCommon")
    public static NProxyCommon proxy;
    public static final String modID = "Natura";
    /* Instance of this mod, used for grabbing prototype fields */
    @Instance(modID)
    public static Natura instance;
    public static Material cloud = new CloudMaterial();

    public static Logger logger = LogManager.getLogger(modID);

    public static final ModuleController moduleLoader = new ModuleController("Natura-Dynamic.cfg", modID);

    @EventHandler
    public void preInit (FMLPreInitializationEvent evt)
    {
        MinecraftForge.EVENT_BUS.register(this);

        PluginController.registerBuiltins();

        PHNatura.initProps(evt.getSuggestedConfigurationFile());
        NaturaTab.tab = new TabTools("natura.plants");
        NaturaTab.woodTab = new TabTools("natura.trees");
        NaturaTab.netherTab = new TabTools("natura.nether");

        content = new NContent();
        content.preInit();
        content.addOredictSupport();
        NaturaTab.tab.init(new ItemStack(NContent.boneBag, 0));
        NaturaTab.woodTab.init(new ItemStack(NContent.floraSapling, 1, 3));
        NaturaTab.netherTab.init(new ItemStack(NContent.floraSapling, 1, 5));

        moduleLoader.preInit();
    }

    public static BaseCropWorldgen crops;
    public static BaseCloudWorldgen clouds;
    public static BaseTreeWorldgen trees;

    @EventHandler
    public void init (FMLInitializationEvent evt)
    {
        GameRegistry.registerWorldGenerator(crops = new BaseCropWorldgen(), 20); // TODO 1.7 Find correct weight (param 2)
        GameRegistry.registerWorldGenerator(clouds = new BaseCloudWorldgen(), 20); // TODO 1.7 Find correct weight (param 2)
        GameRegistry.registerWorldGenerator(trees = new BaseTreeWorldgen(), 20); // TODO 1.7 Find correct weight (param 2)

        proxy.registerRenderer();
        proxy.addNames();
        NetworkRegistry.INSTANCE.registerGuiHandler(instance, new NGuiHandler());

        GameRegistry.registerFuelHandler(content);

        if (PHNatura.overrideNether)
        {
            DimensionManager.unregisterProviderType(-1);
            DimensionManager.registerProviderType(-1, NetheriteWorldProvider.class, true);
        }
        MinecraftForge.EVENT_BUS.register(WorldHandler.instance);

        if (retrogen)
        {
            FMLCommonHandler.instance().bus().register(new TickHandlerWorld());
        }

        OreDictionary.registerOre("cropVine", new ItemStack(NContent.thornVines));
        random.setSeed(2 ^ 16 + 2 ^ 8 + (4 * 3 * 271));

        moduleLoader.init();
    }

    @EventHandler
    public void postInit (FMLPostInitializationEvent evt)
    {
        content.createEntities();
        content.modIntegration();

        moduleLoader.postInit();
    }

    @SubscribeEvent
    public void bonemealEvent (BonemealEvent event)
    {
        if (!event.world.isRemote)
        {
            if (event.block == NContent.glowshroom)
            {
                if (NContent.glowshroom.fertilizeMushroom(event.world, event.x, event.y, event.z, event.world.rand))
                    event.setResult(event.getResult().ALLOW);
            }
            if (event.block == NContent.berryBush)
            {
                if (NContent.berryBush.boneFertilize(event.world, event.x, event.y, event.z, event.world.rand))
                    event.setResult(event.getResult().ALLOW);
            }
            if (event.block == NContent.netherBerryBush)
            {
                if (NContent.netherBerryBush.boneFertilize(event.world, event.x, event.y, event.z, event.world.rand))
                    event.setResult(event.getResult().ALLOW);
            }
        }
    }

    @SubscribeEvent
    public void interactEvent (EntityInteractEvent event)
    {
        //if (event.target == null)
        if (event.target instanceof EntityCow || event.target instanceof EntitySheep)
        {
            ItemStack equipped = event.entityPlayer.getCurrentEquippedItem();
            EntityAnimal creature = (EntityAnimal) event.target;
            if (equipped != null && equipped.getItem() == NContent.plantItem && equipped.getItemDamage() == 0 && creature.getGrowingAge() == 0 && creature.isInLove())
            {
                EntityPlayer player = event.entityPlayer;
                if (!player.capabilities.isCreativeMode)
                {
                    --equipped.stackSize;

                    if (equipped.stackSize <= 0)
                    {
                        player.inventory.setInventorySlotContents(player.inventory.currentItem, (ItemStack) null);
                    }
                }

                creature.func_146082_f(event.entityPlayer);
                creature.setTarget(null);

                for (int i = 0; i < 7; ++i)
                {
                    double d0 = random.nextGaussian() * 0.02D;
                    double d1 = random.nextGaussian() * 0.02D;
                    double d2 = random.nextGaussian() * 0.02D;
                    creature.worldObj.spawnParticle("heart", creature.posX + random.nextFloat() * creature.width * 2.0F - creature.width, creature.posY + 0.5D + random.nextFloat() * creature.height,
                            creature.posZ + random.nextFloat() * creature.width * 2.0F - creature.width, d0, d1, d2);
                }
            }
        }
    }

    @SubscribeEvent
    public void spawnEvent (EntityJoinWorldEvent event)
    {
        if (event.entity instanceof EntityCow || event.entity instanceof EntitySheep)
        {
            ((EntityLiving) event.entity).tasks.addTask(3, new EntityAITempt((EntityCreature) event.entity, 0.25F, NContent.plantItem, false));
        }

        if (event.entity instanceof EntityChicken)
        {
            ((EntityLiving) event.entity).tasks.addTask(3, new EntityAITempt((EntityCreature) event.entity, 0.25F, NContent.seeds, false));
        }
    }

    public static boolean retrogen;

    @SubscribeEvent
    public void chunkDataSave (ChunkDataEvent.Save event)
    {
        event.getData().setBoolean("Natura.Retrogen", true);
    }

    NContent content;
    public static Random random = new Random();
}
