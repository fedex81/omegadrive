package omegadrive.z80;

import emulib.plugins.cpu.DisassembledInstruction;
import omegadrive.z80.disasm.Z80Disasm;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import z80core.Z80State;

import java.util.function.Function;

/**
 * Z80Helper
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2019
 */
public class Z80Helper {

    public static final Function<DisassembledInstruction, String> disasmToString = d ->
            String.format("%08x   %12s   %s", d.getAddress(), d.getOpCode(), d.getMnemo());
    private final static Logger LOG = LogManager.getLogger(Z80Helper.class.getSimpleName());
    public static boolean verbose = false;

    public static String toString(Z80State state) {
        String str = "\n";
        str += String.format("SP: %04x   PC: %04x  I : %02x   R : %02x  IX: %04x  IY: %04x\n",
                state.getRegSP(), state.getRegPC(), state.getRegI(), state.getRegR(), state.getRegIX(), state.getRegIY());
        str += String.format("A : %02x   B : %02x  C : %02x   D : %02x  E : %02x  F : %02x   L : %02x   H : %02x\n",
                state.getRegA(), state.getRegB(),
                state.getRegC(), state.getRegD(), state.getRegE(), state.getRegF(), state.getRegL(), state.getRegH());
        str += String.format("Ax: %02x   Bx: %02x  Cx: %02x   Dx: %02x  Ex: %02x  Fx: %02x   Lx: %02x   Hx: %02x\n",
                state.getRegAx(), state.getRegBx(),
                state.getRegCx(), state.getRegDx(), state.getRegEx(), state.getRegFx(), state.getRegLx(), state.getRegHx());
        str += String.format("AF : %04x   BC : %04x  DE : %04x   HL : %04x\n",
                state.getRegAF(), state.getRegBC(), state.getRegDE(), state.getRegHL());
        str += String.format("AFx: %04x   BCx: %04x  DEx: %04x   HLx: %04x\n",
                state.getRegAFx(), state.getRegBCx(), state.getRegDEx(), state.getRegHLx());
        str += String.format("IM: %s  iff1: %s  iff2: %s  memPtr: %04x  flagQ: %s\n",
                state.getIM().name(), state.isIFF1(), state.isIFF2(), state.getMemPtr(), state.isFlagQ());
        str += String.format("NMI: %s  INTLine: %s  pendingE1: %s\n", state.isNMI(), state.isINTLine(),
                state.isPendingEI());
        return str;
    }

    public static String dumpInfo(Z80Disasm z80Disasm, int pc) {
        return disasmToString.apply(z80Disasm.disassemble(pc));
    }

    private void printVerbose(Z80Disasm z80Disasm, int pc) {
        if (verbose) {
            LOG.info(Z80Helper.dumpInfo(z80Disasm, pc));
        }
    }

    private void printState(Z80State state) {
        if (verbose) {
            LOG.info("Z80State: " + Z80Helper.toString(state));
        }
    }
}
