package com.dreammaster.bartworksHandler;

import static bartworks.API.recipe.BartWorksRecipeMaps.bacterialVatRecipes;
import static com.dreammaster.bartworksHandler.BacteriaRegistry.CultureSet;
import static gregtech.api.enums.GTValues.*;
import static gregtech.api.enums.Materials.NaquadahEnriched;
import static gregtech.api.enums.Materials.Plutonium;
import static gregtech.api.enums.Materials.Uranium;
import static gregtech.api.enums.Mods.BartWorks;
import static gregtech.api.enums.Mods.PamsHarvestCraft;
import static gregtech.api.recipe.RecipeMaps.centrifugeRecipes;
import static gregtech.api.recipe.RecipeMaps.chemicalReactorRecipes;
import static gregtech.api.recipe.RecipeMaps.fluidExtractionRecipes;
import static gregtech.api.recipe.RecipeMaps.mixerRecipes;
import static gregtech.api.recipe.RecipeMaps.multiblockChemicalReactorRecipes;
import static gregtech.api.util.GTRecipeBuilder.MINUTES;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;
import static gregtech.api.util.GTRecipeConstants.GLASS;
import static gregtech.api.util.GTRecipeConstants.SIEVERTS;
import static gregtech.api.util.GTRecipeConstants.UniversalChemical;
import gregtech.api.recipe.metadata.Sieverts;

import bartworks.util.BWUtil;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

import com.dreammaster.main.MainRegistry;

import bartworks.MainMod;
import bartworks.common.items.SimpleSubItemClass;
import bartworks.common.loaders.BioItemList;
import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.TierEU;
import gregtech.api.objects.GTFluid;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTUtility;

public class BioItemLoader {

    private static Item BIOTEMS;
    private static GTFluid[] BIOFLUIDS;

    public static boolean preInit() {
        try {
            BIOTEMS = new SimpleSubItemClass(new String[] { "itemCollagen", "itemGelatin", "itemAgar" })
                    .setCreativeTab(MainMod.BIO_TAB);
            GameRegistry.registerItem(BIOTEMS, "GTNHBioItems", BartWorks.ID);
            BIOFLUIDS = new GTFluid[] {
                    new GTFluid("GelatinMixture", "molten.autogenerated", new short[] { 255, 255, 125 }),
                    new GTFluid("MeatExtract", "molten.autogenerated", new short[] { 160, 70, 50 }),
                    new GTFluid("UnknownNutrientAgar", "molten.autogenerated", new short[] { 175, 133, 0 }),
                    new GTFluid("SeaweedBroth", "molten.autogenerated", new short[] { 60, 200, 0 }) };

            for (GTFluid gtFluid : BIOFLUIDS) {
                FluidRegistry.registerFluid(gtFluid);
            }
            return true;
        } catch (Exception e) {
            MainRegistry.Logger.error(e);
            return false;
        }
    }

    public static void registerRecipes() {
        if (BIOTEMS == null || BIOFLUIDS == null) {
            throw new IllegalStateException("Called registerRecipes without calling preInit first.");
        }

        GTValues.RA.stdBuilder().itemInputs(Materials.MeatRaw.getDust(2), new ItemStack(Items.bone, 1))
                .itemOutputs(new ItemStack(BIOTEMS, 2, 0)).fluidInputs(Materials.DilutedSulfuricAcid.getFluid(1000))
                .fluidOutputs(Materials.Water.getFluid(1000)).duration(1 * MINUTES + 20 * SECONDS).eut(TierEU.RECIPE_HV)
                .addTo(UniversalChemical);

        GTValues.RA.stdBuilder().itemInputs(Materials.MeatRaw.getDust(1), Materials.Bone.getDust(2))
                .itemOutputs(new ItemStack(BIOTEMS, 1, 0)).fluidInputs(Materials.DilutedSulfuricAcid.getFluid(500))
                .fluidOutputs(Materials.Water.getFluid(500)).duration(40 * SECONDS).eut(TierEU.RECIPE_HV)
                .addTo(UniversalChemical);

        GTValues.RA.stdBuilder().itemInputs(new ItemStack(BIOTEMS, 4, 0), Materials.Water.getCells(3))
                .itemOutputs(Materials.Empty.getCells(3)).fluidInputs(Materials.PhosphoricAcid.getFluid(1000))
                .fluidOutputs(new FluidStack(BIOFLUIDS[0], 4000)).duration(1 * MINUTES + 20 * SECONDS)
                .eut(TierEU.RECIPE_HV).addTo(UniversalChemical);

        GTValues.RA.stdBuilder().itemInputs(new ItemStack(BIOTEMS, 4, 0), Materials.PhosphoricAcid.getCells(1))
                .itemOutputs(Materials.Empty.getCells(1)).fluidInputs(Materials.Water.getFluid(3000))
                .fluidOutputs(new FluidStack(BIOFLUIDS[0], 4000)).duration(1 * MINUTES + 20 * SECONDS)
                .eut(TierEU.RECIPE_HV).addTo(chemicalReactorRecipes);

        GTValues.RA.stdBuilder().itemInputs(GTUtility.getIntegratedCircuit(1))
                .itemOutputs(Materials.Phosphorus.getDust(1), new ItemStack(BIOTEMS, 4, 1))
                .fluidInputs(new FluidStack(BIOFLUIDS[0], 6000)).duration(2 * MINUTES).eut(TierEU.RECIPE_HV)
                .addTo(centrifugeRecipes);

        RA.stdBuilder().itemInputs(GTUtility.getIntegratedCircuit(11), new ItemStack(BIOTEMS, 1, 1))
                .itemOutputs(new ItemStack(BIOTEMS, 1, 2)).fluidInputs(GTModHandler.getDistilledWater(1000))
                .duration(30 * SECONDS).eut(TierEU.RECIPE_HV).addTo(mixerRecipes);

        GTValues.RA.stdBuilder().itemInputs(Materials.MeatRaw.getDust(1))
                .fluidOutputs(new FluidStack(BIOFLUIDS[1], 125)).duration(15 * SECONDS).eut(TierEU.RECIPE_MV)
                .addTo(fluidExtractionRecipes);

        GTValues.RA.stdBuilder()
                .itemInputs(
                        new ItemStack(BIOTEMS, 8, 2),
                        ItemList.Circuit_Chip_Stemcell.get(16),
                        Materials.Salt.getDust(64))
                .fluidInputs(
                        FluidRegistry.getFluidStack("unknowwater", 4000),
                        Materials.PhthalicAcid.getFluid(3000),
                        new FluidStack(BIOFLUIDS[1], 1000))
                .fluidOutputs(new FluidStack(BIOFLUIDS[2], 8000)).duration(60 * SECONDS).eut(TierEU.RECIPE_UV)
                .addTo(multiblockChemicalReactorRecipes);

        GTValues.RA.stdBuilder()
                .itemInputs(
                        ItemList.IC2_Energium_Dust.get(8),
                        Materials.Mytryl.getDust(1),
                        GTModHandler.getModItem(PamsHarvestCraft.ID, "seaweedItem", 64))
                .special(BioItemList.getPetriDish(CultureSet.get("TcetiEBac")))
                .fluidInputs(new FluidStack(BIOFLUIDS[2], 50)).fluidOutputs(new FluidStack(BIOFLUIDS[3], 50))
                .duration(1 * MINUTES).eut(TierEU.RECIPE_UV)
                .metadata(GLASS, 8)
                .metadata(SIEVERTS, new Sieverts(100, false))
                .noOptimize()
                .addTo(bacterialVatRecipes);

        for (int i = 0; i < OreDictionary.getOres("cropTcetiESeaweed").size(); i++) {
            GTValues.RA.stdBuilder().itemInputs(GTUtility.getIntegratedCircuit(i + 1))
                    .itemOutputs(OreDictionary.getOres("cropTcetiESeaweed").get(i).copy().splitStack(64))
                    .fluidInputs(new FluidStack(BIOFLUIDS[3], 1000)).duration(2 * SECONDS).eut(TierEU.RECIPE_UV)
                    .noOptimize().addTo(centrifugeRecipes);
        }

        GTValues.RA.stdBuilder()
                .itemInputs(
                        Materials.MeatRaw.getDust(4),
                        Materials.Salt.getDust(4),
                        Materials.Calcium.getDust(4),
                        new ItemStack(BIOTEMS, 4, 2))
                .special(BioItemList.getPetriDish(CultureSet.get("OvumBac")))
                .fluidInputs(FluidRegistry.getFluidStack("binnie.bacteria", 4))
                .fluidOutputs(Materials.GrowthMediumRaw.getFluid(1)).duration(1 * MINUTES).eut(TierEU.RECIPE_IV)
                .metadata(GLASS, 5)
                .metadata(SIEVERTS, new Sieverts(BWUtil.calculateSv(Uranium), false))
                .noOptimize()
                .addTo(bacterialVatRecipes);

        GTValues.RA.stdBuilder()
                .itemInputs(
                        Materials.MeatRaw.getDust(8),
                        Materials.Salt.getDust(8),
                        Materials.Calcium.getDust(8),
                        new ItemStack(BIOTEMS, 4, 2))
                .special(BioItemList.getPetriDish(CultureSet.get("OvumBac")))
                .fluidInputs(FluidRegistry.getFluidStack("bacterialsludge", 4))
                .fluidOutputs(Materials.GrowthMediumRaw.getFluid(2)).duration(1 * MINUTES).eut(TierEU.RECIPE_LuV)
                .metadata(GLASS, 6)
                .metadata(SIEVERTS, new Sieverts(BWUtil.calculateSv(Plutonium), false))
                .noOptimize()
                .addTo(bacterialVatRecipes);

        GTValues.RA.stdBuilder()
                .itemInputs(
                        Materials.MeatRaw.getDust(12),
                        Materials.Salt.getDust(12),
                        Materials.Calcium.getDust(12),
                        new ItemStack(BIOTEMS, 4, 2))
                .special(BioItemList.getPetriDish(CultureSet.get("OvumBac")))
                .fluidInputs(FluidRegistry.getFluidStack("mutagen", 4))
                .fluidOutputs(Materials.GrowthMediumRaw.getFluid(4)).duration(1 * MINUTES).eut(TierEU.RECIPE_ZPM)
                .metadata(GLASS, 7)
                .metadata(SIEVERTS, new Sieverts(BWUtil.calculateSv(NaquadahEnriched), true))
                .noOptimize()
                .addTo(bacterialVatRecipes);
    }
}
