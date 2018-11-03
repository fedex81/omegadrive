package omegadrive.vdp;

import omegadrive.bus.BusProvider;
import omegadrive.util.VideoMode;

import java.util.EnumSet;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 */
public interface VdpProvider {

    enum VdpRamType {
        VRAM,
        CRAM,
        VSRAM;
    }

    enum VramMode {
        vramRead(0b0000, VdpRamType.VRAM),
        cramRead(0b1000, VdpRamType.CRAM),
        vsramRead(0b0100, VdpRamType.VSRAM),
        vramWrite(0b0001, VdpRamType.VRAM),
        cramWrite(0b0011, VdpRamType.CRAM),
        vsramWrite(0b0101, VdpRamType.VSRAM),
        vramRead_8bit(0b1100, VdpRamType.VRAM);

        private static EnumSet<VramMode> values = EnumSet.allOf(VramMode.class);

        private VdpRamType ramType;
        private int addressMode;

        VramMode(int addressMode, VdpRamType ramType) {
            this.ramType = ramType;
            this.addressMode = addressMode;
        }

        public static VramMode getVramMode(int addressMode) {
            for (VramMode mode : VramMode.values) {
                if (mode.addressMode == addressMode) {
                    return mode;
                }
            }
            return null;
        }

        public VdpRamType getRamType() {
            return ramType;
        }

        public boolean isWriteMode() {
            return this == vramWrite || this == vsramWrite || this == cramWrite;
        }
    }

    int MAX_SPRITES_PER_FRAME_H40 = 80;
    int MAX_SPRITES_PER_FRAME_H32 = 64;
    int MAX_SPRITES_PER_LINE_H40 = 20;
    int MAX_SPRITES_PER_LINE_H32 = 16;
    int VERTICAL_LINES_V30 = 240;
    int VERTICAL_LINES_V28 = 224;
    int VDP_VIDEO_ROWS = 256;
    int VDP_VIDEO_COLS = 320;

    //	The CRAM contains 128 bytes, addresses 0 to 7F
    int VDP_CRAM_SIZE = 0x80;

    //	The VSRAM contains 80 bytes, addresses 0 to 4F
    int VDP_VSRAM_SIZE = 0x50;

    int VDP_VRAM_SIZE = 0x10000;

    int VDP_REGISTERS_SIZE = 24;

    int V28_CELL = 224;
    int V30_CELL = 240;
    int H40 = 320;
    int H32 = 256;

    static VdpProvider createVdp(BusProvider bus) {
        return new GenesisVdp(bus);
    }

    void init();

    void run(int cycles);

    //always a word
    int readDataPort();

    void writeDataPort(long data);

    int readControl();

    void writeControlPort(long data);

    int getVCounter();

    int getHCounter();

    boolean isIe0();

    boolean isIe1();

    int getRegisterData(int reg);

    void updateRegisterData(int reg, int data);

    void setDmaFlag(int value);

    /**
     * State of the Vertical interrupt pending flag
     *
     * @return
     */
    boolean getVip();

    /**
     * Set the Vertical interrupt pending flag
     * @return
     */
    void setVip(boolean value);

    /**
     * State of the Horizontal interrupt pending flag
     *
     * @return
     */
    boolean getHip();

    /**
     * Set the Horizontal interrupt pending flag
     *
     * @return
     */
    void setHip(boolean value);

    boolean isDisplayEnabled();

    VideoMode getVideoMode();

    default void dumpScreenData() {
        throw new UnsupportedOperationException("Not supported");
    }
}
