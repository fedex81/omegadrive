package omegadrive.automated;

import com.google.common.collect.ImmutableMap;
import omegadrive.Genesis;
import omegadrive.save.SavestateTest;
import omegadrive.util.Util;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public class SavestateGameLoader {

    static String romFolder = "/home/fede/roms/savestate_test";

    private static String saveStateFolder = SavestateTest.saveStateFolder.toAbsolutePath().toString();

    public static Map<String, String> saveStates = ImmutableMap.<String, String>builder().
            put("test01.gs0", "Sonic The Hedgehog (W) (REV00) [!].bin").
            put("test02.gs0", "Sonic The Hedgehog 2 (W) (REV01) [!].bin").
            put("sor2.gs0", "Streets of Rage 2 (U) [!].bin").
            put("sor2.gs1", "Streets of Rage 2 (U) [!].bin").
            put("sor2.gs2", "Streets of Rage 2 (U) [!].bin").
            put("sor2.gs3", "Streets of Rage 2 (U) [!].bin").
            put("BATROB.gs0", "Adventures of Batman and Robin, The (U) [!].bin").
            put("BATROB.gs9", "Adventures of Batman and Robin, The (U) [!].bin").
            put("COMIX_ZN.GS0", "Comix Zone (U) [!].bin").
            put("CONTRA4.GS0", "Contra - The Hard Corps (J) [!].bin"). //broken
            put("D_HEDD_T.gs0", "Dynamite Headdy (J) [c][!].bin").
            put("FANTASI.gs0", "Fantasia (E) [!].bin").
            put("FANTASI.gs9", "Fantasia (E) [!].bin").
            put("G-AXE.GS0", "Golden Axe (W) (REV00) [!].bin").
            put("G-AXE.GS1", "Golden Axe (W) (REV00) [!].bin").
            put("G-AXE.GS2", "Golden Axe (W) (REV00) [!].bin").
            put("G-AXE.GS4", "Golden Axe (W) (REV00) [!].bin").
            put("G-AXE.GS8", "Golden Axe (W) (REV00) [!].bin").
            put("G-AXE.GS9", "Golden Axe (W) (REV00) [!].bin").
            put("G-AXE2.GS1", "Golden Axe II (W) [!].bin").
            put("G-AXE2.GS2", "Golden Axe II (W) [!].bin").
            put("mickeym.gs0", "Mickey Mania - Timeless Adventures of Mickey Mouse (E) [!].bin").
            put("mickeym.gs1", "Mickey Mania - Timeless Adventures of Mickey Mouse (E) [!].bin").
            put("mickeym.gs2", "Mickey Mania - Timeless Adventures of Mickey Mouse (E) [!].bin").
            put("mickeym.gs3", "Mickey Mania - Timeless Adventures of Mickey Mouse (E) [!].bin").
            put("mickeym.gs9", "Mickey Mania - Timeless Adventures of Mickey Mouse (E) [!].bin").
            put("MORTAL2.GS0", "Mortal Kombat II (W) [!].bin").
            put("Rocket2.gs0", "Sparkster (U) [!].bin").
            put("Rocket2.gs8", "Sparkster (U) [!].bin").
            put("Rocket2.gs9", "Sparkster (U) [!].bin").
            put("SONIC3D.GS0", "Sonic 3D Blast (UE) [!].bin").
            put("SONIC3D.GS1", "Sonic 3D Blast (UE) [!].bin").
            put("Thunder4.gs0", "Thunder Force IV (E) [c][!].bin").
            put("Vector2.gs5", "Vectorman 2 (U) [!].bin").
            put("Vector2.gs6", "Vectorman 2 (U) [!].bin").
            put("Vector2.gs7", "Vectorman 2 (U) [!].bin").
            put("VECTORMA.GS0", "Vectorman (UE) [!].bin").
            build();


    static int saveStateTestNumber = 21;

    public static void main(String[] args) throws Exception {
//        loadOne();
        loadAll(false);
    }

    public static void loadOne() throws Exception {
        Genesis genesis = new Genesis(false);
        load(genesis, saveStates.keySet().toArray()[saveStateTestNumber].toString(),
                saveStates.values().toArray()[saveStateTestNumber].toString());
        Util.sleep(10_000);
        genesis.handleCloseApp();
    }

    public static void loadAll(boolean loop) throws Exception {
        Genesis genesis = new Genesis(false);
        do {
            for (Map.Entry<String, String> entry : saveStates.entrySet()) {
                load(genesis, entry.getKey(), entry.getValue());
                Util.sleep(10_000);
                genesis.handleCloseGame();
                Util.sleep(1_000);
            }
        } while (loop);
        genesis.handleCloseApp();
    }


    private static void load(Genesis genesis, String saveFileName, String romFile) {
        Path rom = Paths.get(romFolder, romFile);
        Path saveFile = Paths.get(saveStateFolder, saveFileName);
        System.out.println("Loading ROM: " + rom.toAbsolutePath().toString());
        System.out.println("Loading state file: " + rom);

        genesis.handleNewGame(rom);
        Util.sleep(1_000);
        genesis.handleLoadState(saveFile);
    }
}