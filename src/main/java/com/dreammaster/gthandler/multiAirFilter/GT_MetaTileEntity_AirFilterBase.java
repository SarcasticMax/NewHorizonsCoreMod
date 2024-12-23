package com.dreammaster.gthandler.multiAirFilter;

import static com.dreammaster.gthandler.casings.GT_Container_CasingsNH.sBlockCasingsNH;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.*;
import static gregtech.api.enums.GTValues.VN;
import static gregtech.api.enums.Textures.BlockIcons.TURBINE_NEW;
import static gregtech.api.enums.Textures.BlockIcons.TURBINE_NEW_ACTIVE;
import static gregtech.api.util.GTStructureUtility.ofHatchAdder;
import static gregtech.api.util.GTStructureUtility.ofHatchAdderOptional;
import static gregtech.api.util.GTUtility.filterValidMTEs;
import static java.lang.Math.max;
import static java.lang.Math.min;

import java.util.ArrayList;
import java.util.Arrays;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.jetbrains.annotations.NotNull;

import com.dreammaster.gthandler.CustomItemList;
import com.dreammaster.item.ItemList;
import com.dreammaster.main.MainRegistry;
import com.gtnewhorizon.structurelib.alignment.IAlignmentLimits;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import eu.usrv.yamcore.auxiliary.PlayerChatHelper;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.IToolStats;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.items.MetaGeneratedTool;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEHatchMuffler;
import gregtech.api.objects.GTRenderedTexture;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;
import gregtech.api.util.GTUtilityClient;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.items.MetaGeneratedTool01;
import gregtech.common.pollution.Pollution;

// TODO move this multi to GT5u
public abstract class GT_MetaTileEntity_AirFilterBase
        extends MTEEnhancedMultiBlockBase<GT_MetaTileEntity_AirFilterBase> {

    protected int baseEff = 0;
    protected int multiTier = 0;
    protected int chunkIndex = 0;
    protected boolean hasPollution = false;
    protected int mode = 0; // 0 for processing chunks in order, 1 for processing chunks randomly
    protected int size; // current working size of the multi, max is 2*multiTier + 1
    protected boolean isFilterLoaded = false;
    protected int filterUsageRemaining = 0;
    protected int tickCounter = 0; // because we can't trust the world tick, it may be in a dim with eternal day, etc
    private boolean mFormed;
    protected static final String STRUCTURE_PIECE_MAIN = "main";
    protected static final ClassValue<IStructureDefinition<GT_MetaTileEntity_AirFilterBase>> STRUCTURE_DEFINITION = new ClassValue<IStructureDefinition<GT_MetaTileEntity_AirFilterBase>>() {

        @Override
        protected IStructureDefinition<GT_MetaTileEntity_AirFilterBase> computeValue(Class<?> type) {
            return StructureDefinition.<GT_MetaTileEntity_AirFilterBase>builder()
                    .addShape(
                            STRUCTURE_PIECE_MAIN,
                            transpose(
                                    new String[][] { { "xxx", "xxx", "xxx" }, { "vmv", "m-m", "vmv" },
                                            { "vmv", "m-m", "vmv" }, { "c~c", "ccc", "ccc" }, }))
                    .addElement(
                            'c',
                            lazy(
                                    x -> ofChain(
                                            ofBlock(sBlockCasingsNH, x.getCasingMeta()),
                                            ofHatchAdder(
                                                    GT_MetaTileEntity_AirFilterBase::addMaintenanceToMachineList,
                                                    x.getCasingIndex(),
                                                    1),
                                            ofHatchAdder(
                                                    GT_MetaTileEntity_AirFilterBase::addInputToMachineList,
                                                    x.getCasingIndex(),
                                                    1),
                                            ofHatchAdder(
                                                    GT_MetaTileEntity_AirFilterBase::addOutputToMachineList,
                                                    x.getCasingIndex(),
                                                    1),
                                            ofHatchAdder(
                                                    GT_MetaTileEntity_AirFilterBase::addEnergyInputToMachineList,
                                                    x.getCasingIndex(),
                                                    1))))
                    .addElement('x', lazy(x -> ofBlock(sBlockCasingsNH, x.getCasingMeta())))
                    .addElement('v', lazy(x -> ofBlock(sBlockCasingsNH, x.getPipeMeta())))
                    .addElement(
                            'm',
                            lazy(
                                    x -> ofHatchAdderOptional(
                                            GT_MetaTileEntity_AirFilterBase::addMufflerToMachineList,
                                            x.getCasingIndex(),
                                            2,
                                            sBlockCasingsNH,
                                            x.getCasingMeta())))
                    .build();
        }
    };

    @Override
    public final IStructureDefinition<GT_MetaTileEntity_AirFilterBase> getStructureDefinition() {
        return STRUCTURE_DEFINITION.get(getClass());
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece(STRUCTURE_PIECE_MAIN, stackSize, hintsOnly, 1, 3, 0);
    }

    @Override
    public boolean checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack) {
        return checkPiece(STRUCTURE_PIECE_MAIN, 1, 3, 0) && !mMufflerHatches.isEmpty()
                && mMaintenanceHatches.size() == 1;
    }

    static final GTRecipe tRecipeT1 = new GTRecipe(
            new ItemStack[] { CustomItemList.AdsorptionFilter.get(1L, new Object()) },
            new ItemStack[] { CustomItemList.AdsorptionFilterDirty.get(1L, new Object()) },
            null,
            null,
            null,
            null,
            200,
            30,
            0);
    static final GTRecipe tRecipeT2 = new GTRecipe(
            new ItemStack[] { CustomItemList.AdsorptionFilter.get(1L, new Object()) },
            new ItemStack[] { CustomItemList.AdsorptionFilterDirty.get(1L, new Object()) },
            null,
            null,
            null,
            null,
            200,
            480,
            0);
    static final GTRecipe tRecipeT3 = new GTRecipe(
            new ItemStack[] { CustomItemList.AdsorptionFilter.get(1L, new Object()) },
            new ItemStack[] { CustomItemList.AdsorptionFilterDirty.get(1L, new Object()) },
            null,
            null,
            null,
            null,
            200,
            7680,
            0);

    public GT_MetaTileEntity_AirFilterBase(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public GT_MetaTileEntity_AirFilterBase(String aName) {
        super(aName);
    }

    public abstract GTRecipe getRecipe();

    public String getCasingString() {
        return switch (getCasingMeta()) {
            case 0 -> "Air Filter Turbine Casing";
            case 3 -> "Advanced Air Filter Turbine Casing";
            case 5 -> "Super Air Filter Turbine Casing";
            default -> "fill a ticket on github if you read this";
        };
    }

    public String getPipeString() {
        return switch (getPipeMeta()) {
            case 1 -> "Air Filter Vent Casing";
            case 4 -> "Advanced Air Filter Vent Casing";
            case 6 -> "Super Air Filter Vent Casing";
            default -> "fill a ticket on github if you read this";
        };
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType("Air Filter").addInfo("Needs a Turbine in the controller")
                .addInfo("Can process " + (2 * multiTier + 1) + "x" + (2 * multiTier + 1) + " chunks")
                .addInfo("Each muffler hatch reduces pollution in one chunk of the working area by:")
                .addInfo(
                        "  " + EnumChatFormatting.WHITE
                                + MainRegistry.CoreConfig.globalMultiplicator
                                + " * multiTierBonus * turbineEff * FLOOR("
                                + MainRegistry.CoreConfig.scalingFactor
                                + "^mufflerTier)")
                .addInfo("every second").addInfo("- multiTierBonus for this controller is " + getBonusByTier())
                .addInfo("- turbineEff is the efficiency of the Turbine in controller slot")
                .addInfo("- Effective muffler tier is limited by energy input tier")
                .addInfo("- Uses " + this.getRecipe().mEUt + " EU/t while working").addSeparator()
                .addInfo("Insert " + ItemList.AdsorptionFilter.getIS().getDisplayName() + " in an input bus")
                .addInfo("  to double pollution cleaning amount (30 uses per item)")
                .addInfo("Each maintenance issue reduces cleaning amount by 10%").beginStructureBlock(3, 4, 3, true)
                .addController("Front bottom").addOtherStructurePart(getCasingString(), "Top and bottom layers")
                .addOtherStructurePart(getPipeString(), "Corners of the middle two layers")
                .addOtherStructurePart("Muffler Hatch", "Sides of the middle two layers")
                .addEnergyHatch("Any bottom layer casing", 1).addMaintenanceHatch("Any bottom layer casing", 1)
                .addInputBus("Any bottom layer casing", 1).addOutputBus("Any bottom layer casing", 1).toolTipFinisher();
        return tt;
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
            int colorIndex, boolean aActive, boolean aRedstone) {
        if (side == facing) {
            return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(getCasingIndex()),
                    new GTRenderedTexture(
                            aActive ? Textures.BlockIcons.OVERLAY_FRONT_DIESEL_ENGINE_ACTIVE
                                    : Textures.BlockIcons.OVERLAY_FRONT_DIESEL_ENGINE) };
        }
        return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(getCasingIndex()) };
    }

    public abstract float getBonusByTier();

    public abstract int getCasingIndex();

    @Override
    public boolean isCorrectMachinePart(ItemStack aStack) {
        if (aStack == null) return false;
        if (!(aStack.getItem() instanceof MetaGeneratedTool01 tool)) return false;
        if (aStack.getItemDamage() < 170 || aStack.getItemDamage() > 179) return false;

        IToolStats stats = tool.getToolStats(aStack);
        if (stats == null || stats.getSpeedMultiplier() <= 0) return false;

        Materials material = MetaGeneratedTool.getPrimaryMaterial(aStack);
        return material != null && material.mToolSpeed > 0;
    }

    private float getTurbineDamage(ItemStack aStack) {
        if (aStack == null || !(aStack.getItem() instanceof MetaGeneratedTool tool)) {
            return -1;
        }
        return tool.getToolCombatDamage(aStack);
    }

    @Override
    protected IAlignmentLimits getInitialAlignmentLimits() {
        // don't rotate it, it's cursed.
        return (d, r, f) -> d.offsetY == 0 && r.isNotRotated() && !f.isVerticallyFliped();
    }

    public int getPollutionCleaningRatePerTick(float turbineEff, float multiEff, boolean isRateBoosted) {
        return getPollutionCleaningRatePerSecond(turbineEff, multiEff, isRateBoosted) / 20;
    }

    public int getPollutionCleaningRatePerSecond(float turbineEff, float multiEff, boolean isRateBoosted) {
        long tVoltage = getMaxInputVoltage();
        byte tTier = (byte) max(1, GTUtility.getTier(tVoltage));
        int pollutionPerSecond = 0;
        for (MTEHatchMuffler tHatch : filterValidMTEs(mMufflerHatches)) {
            // applying scaling factor
            pollutionPerSecond += (int) Math.pow(MainRegistry.CoreConfig.scalingFactor, min(tTier, tHatch.mTier));
        }
        // apply the boost
        if (isRateBoosted) {
            pollutionPerSecond = (int) (pollutionPerSecond * MainRegistry.CoreConfig.boostPerAbsorptionFilter);
        }
        // apply the rest of the coefs
        pollutionPerSecond = (int) (pollutionPerSecond * turbineEff
                * multiEff
                * getBonusByTier()
                * MainRegistry.CoreConfig.globalMultiplicator);
        return pollutionPerSecond;
    }

    @NotNull
    @Override
    public CheckRecipeResult checkProcessing() {
        mEfficiencyIncrease = 10000;
        mEfficiency = 10000 - (getIdealStatus() - getRepairStatus()) * 1000;
        // check pollution for next cycle:
        hasPollution = getTotalPollution() >= MainRegistry.CoreConfig.pollutionThresholdAirFilter;
        mMaxProgresstime = getRecipe().mDuration;
        mEUt = -getRecipe().mEUt;
        if (!hasPollution) {
            return CheckRecipeResultRegistry.SUCCESSFUL;
        }

        ItemStack aStack = getControllerSlot();
        if (!isCorrectMachinePart(aStack)) {
            return CheckRecipeResultRegistry.NO_TURBINE_FOUND;
        }

        float damage = getTurbineDamage(aStack);
        if (damage == -1) {
            return CheckRecipeResultRegistry.NO_TURBINE_FOUND;
        }
        baseEff = GTUtility.safeInt((long) ((50.0F + 10.0F * damage) * 100));
        tickCounter = 0; // resetting the counter in case of a power failure, etc

        // scan the inventory to search for filter if none has been loaded previously
        if (!isFilterLoaded) {
            ArrayList<ItemStack> tInputList = getStoredInputs();
            int tInputList_sS = tInputList.size();
            for (int i = 0; i < tInputList_sS - 1; i++) {
                for (int j = i + 1; j < tInputList_sS; j++) {
                    if (GTUtility.areStacksEqual(tInputList.get(i), tInputList.get(j))) {
                        if (tInputList.get(i).stackSize >= tInputList.get(j).stackSize) {
                            tInputList.remove(j--);
                            tInputList_sS = tInputList.size();
                        } else {
                            tInputList.remove(i--);
                            tInputList_sS = tInputList.size();
                            break;
                        }
                    }
                }
            }

            ItemStack[] tInputs = Arrays.copyOfRange(tInputList.toArray(new ItemStack[0]), 0, 2);
            if (!tInputList.isEmpty()) {
                if (getRecipe().isRecipeInputEqual(true, null, tInputs)) {
                    updateSlots();
                    filterUsageRemaining = MainRegistry.CoreConfig.usagesPerAbsorptionFilter;
                    isFilterLoaded = true;
                }
            }
        }

        // if a filter is loaded in
        if (isFilterLoaded) {

            // consume one use of the filter
            filterUsageRemaining -= 1;

            // when the filter finished its last usage, we give it back in dirty form.
            if (filterUsageRemaining == 0) {
                mOutputItems = new ItemStack[] { getRecipe().getOutput(0) };
                isFilterLoaded = false;
            } else {
                mOutputItems = null; // no return until the filter has been totally consumed
            }
        }

        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger("mode", mode); // running mode
        aNBT.setInteger("chunkIndex", chunkIndex); // chunk index when running in normal mode
        aNBT.setInteger("size", size); // working area
        aNBT.setBoolean("isFilterLoaded", isFilterLoaded);
        aNBT.setInteger("filterUsageRemaining", filterUsageRemaining);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mode = aNBT.getInteger("mode");
        chunkIndex = aNBT.getInteger("chunkIndex");
        size = aNBT.getInteger("size");
        isFilterLoaded = aNBT.getBoolean("isFilterLoaded");
        filterUsageRemaining = aNBT.getInteger("filterUsageRemaining");
    }

    public void cleanPollution() {
        int cleaningRate = getPollutionCleaningRatePerSecond(baseEff / 10000f, mEfficiency / 10000f, isFilterLoaded);
        if (cleaningRate > 0) {
            World world = this.getBaseMetaTileEntity().getWorld();
            if (mode == 0) { // processing chunk normally
                removePollutionFromChunk(cleaningRate, world, chunkIndex);
                chunkIndex += 1;
                if (chunkIndex >= size * size) {
                    chunkIndex = 0;
                }
            } else { // process chunks randomly
                // list all the polluted chunks
                ArrayList<Integer> pollutedChunks = new ArrayList<>();
                for (int index = 0; index < size * size; index++) {
                    if (getPollutionInChunk(world, index) > 0) {
                        pollutedChunks.add(index);
                    }
                }
                // pick the chunk randomly
                if (!pollutedChunks.isEmpty()) {
                    int index = pollutedChunks.get(MainRegistry.Rnd.nextInt(pollutedChunks.size()));
                    removePollutionFromChunk(cleaningRate, world, index);
                }
            }
        }
    }

    protected final int getPollutionInChunk(World world, int chunkIndexIn) {
        final int xCoordMulti = this.getBaseMetaTileEntity().getXCoord();
        final int zCoordMulti = this.getBaseMetaTileEntity().getZCoord();
        final int chunkX = xCoordMulti - 16 * (size / 2 - chunkIndexIn % size) >> 4;
        final int chunkZ = zCoordMulti + 16 * (size / 2 - chunkIndexIn / size) >> 4;
        return Pollution.getPollution(world, chunkX, chunkZ);
    }

    protected final void removePollutionFromChunk(int amount, World world, int chunkIndexIn) {
        final int xCoordMulti = this.getBaseMetaTileEntity().getXCoord();
        final int zCoordMulti = this.getBaseMetaTileEntity().getZCoord();
        final int chunkX = xCoordMulti - 16 * (size / 2 - chunkIndexIn % size) >> 4;
        final int chunkZ = zCoordMulti + 16 * (size / 2 - chunkIndexIn / size) >> 4;
        Pollution.addPollution(world, chunkX, chunkZ, -amount);
    }

    public abstract int getPipeMeta();

    public abstract int getCasingMeta();

    @Override
    public int getMaxEfficiency(ItemStack aStack) {
        return 10000;
    }

    @Override
    public void onPreTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        if (size == 0) { // here in case it's not set by NBT loading
            size = 2 * multiTier + 1;
        }
        super.onPreTick(aBaseMetaTileEntity, aTick);
    }

    public int getTotalPollution() {
        int pollutionAmount = 0;
        World world = this.getBaseMetaTileEntity().getWorld();
        for (int i = 0; i < size * size; i++) {
            pollutionAmount += getPollutionInChunk(world, i);

        }
        return pollutionAmount;
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        if (aTick % 200L == 0L) {
            // refresh casing on state change
            int Xpos = aBaseMetaTileEntity.getXCoord() + aBaseMetaTileEntity.getBackFacing().offsetX;
            int Ypos = aBaseMetaTileEntity.getYCoord() + 3;
            int Zpos = aBaseMetaTileEntity.getZCoord() + aBaseMetaTileEntity.getBackFacing().offsetZ;
            try {
                aBaseMetaTileEntity.getWorld()
                        .markBlockRangeForRenderUpdate(Xpos - 1, Ypos, Zpos - 1, Xpos + 1, Ypos, Zpos + 1);
            } catch (Exception ignored) {}
        }
        super.onPostTick(aBaseMetaTileEntity, aTick);
    }

    @Override
    public void onValueUpdate(byte aValue) {
        mFormed = aValue == 1;
    }

    @Override
    public byte getUpdateData() {
        return (byte) (mMachine ? 1 : 0);
    }

    @Override
    public boolean renderInWorld(IBlockAccess aWorld, int aX, int aY, int aZ, Block aBlock, RenderBlocks aRenderer) {
        if (!mFormed) return false;
        int[] xyz = new int[3];
        ExtendedFacing ext = getExtendedFacing();
        ext.getWorldOffset(new int[] { 0, -3, 1 }, xyz);
        IIconContainer[] tTextures = getBaseMetaTileEntity().isActive() ? TURBINE_NEW_ACTIVE : TURBINE_NEW;
        // we know this multi can only ever face upwards, so just use +y directly
        ExtendedFacing direction = ExtendedFacing.of(ForgeDirection.UP);
        GTUtilityClient.renderTurbineOverlay(
                aWorld,
                xyz[0] + aX,
                xyz[1] + aY,
                xyz[2] + aZ,
                aRenderer,
                direction,
                sBlockCasingsNH,
                tTextures);
        return false;
    }

    @Override
    public boolean onRunningTick(ItemStack aStack) {
        if (tickCounter == 19 && hasPollution) {
            cleanPollution();
            tickCounter = 0;
        } else {
            tickCounter += 1;
        }
        return super.onRunningTick(aStack);
    }

    @Override
    public int getDamageToComponent(ItemStack aStack) {
        try {
            if (isCorrectMachinePart(aStack) && hasPollution) { // no pollution no damage
                return getBaseMetaTileEntity().getRandomNumber(2); // expected to be 0.5 damage in long term
            }
        } catch (Exception e) {
            /**/
        }
        return 0;
    }

    @Override
    public boolean explodesOnComponentBreak(ItemStack aStack) {
        return false;
    }

    @Override
    public void onScrewdriverRightClick(ForgeDirection side, EntityPlayer aPlayer, float aX, float aY, float aZ) {
        if (!aPlayer.isSneaking()) { // change mode
            mode = mode == 1 ? 0 : 1;
            if (mode == 0) {
                chunkIndex = 0;
                PlayerChatHelper.SendInfo(aPlayer, "Electric air filter now running in normal mode");
            } else {
                PlayerChatHelper.SendInfo(aPlayer, "Electric air filter now running in random mode");
            }
        } else { // change radius on sneak
            if (size == 1) {
                size = 2 * multiTier + 1;
            } else {
                size -= 2; // always get odd number
            }
            chunkIndex = 0;
            PlayerChatHelper
                    .SendInfo(aPlayer, "Electric air filter is now working in a " + size + "x" + size + " area");
        }
    }

    @Override
    public String[] getInfoData() {
        return new String[] { "Progress:",
                EnumChatFormatting.GREEN + Integer.toString(mProgresstime / 20)
                        + EnumChatFormatting.RESET
                        + " s / "
                        + EnumChatFormatting.YELLOW
                        + mMaxProgresstime / 20
                        + EnumChatFormatting.RESET
                        + " s",
                "Stored Energy:",
                EnumChatFormatting.GREEN + Long.toString(getBaseMetaTileEntity().getStoredEU())
                        + EnumChatFormatting.RESET
                        + " EU / "
                        + EnumChatFormatting.YELLOW
                        + getBaseMetaTileEntity().getEUCapacity()
                        + EnumChatFormatting.RESET
                        + " EU",
                "Probably uses: " +
                // negative EU triggers special EU consumption behavior. however it does not produce power.
                        EnumChatFormatting.RED + Math.abs(mEUt) + EnumChatFormatting.RESET + " EU/t",
                "Max Energy Income: " + EnumChatFormatting.YELLOW
                        + getMaxInputVoltage()
                        + EnumChatFormatting.RESET
                        + " EU/t(*2A) Tier: "
                        + EnumChatFormatting.YELLOW
                        + VN[GTUtility.getTier(getMaxInputVoltage())]
                        + EnumChatFormatting.RESET,
                "Problems: " + EnumChatFormatting.RED
                        + (getIdealStatus() - getRepairStatus())
                        + EnumChatFormatting.RESET
                        + " Efficiency: "
                        + EnumChatFormatting.YELLOW
                        + mEfficiency / 100.0F
                        + EnumChatFormatting.RESET
                        + " %",
                "Pollution reduction: " + EnumChatFormatting.GREEN
                        + getPollutionCleaningRatePerTick(baseEff / 10000f, mEfficiency / 10000f, isFilterLoaded)
                        + EnumChatFormatting.RESET
                        + " gibbl/t",
                "Has a filter in it: " + isFilterLoaded,
                "remaining cycles for the filter (if present): " + filterUsageRemaining };
    }
}
