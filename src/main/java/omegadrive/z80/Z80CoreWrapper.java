package omegadrive.z80;

import emulib.plugins.cpu.DisassembledInstruction;
import omegadrive.Genesis;
import omegadrive.bus.BusProvider;
import omegadrive.util.LogHelper;
import omegadrive.z80.disasm.Z80Decoder;
import omegadrive.z80.disasm.Z80Disasm;
import omegadrive.z80.disasm.Z80MemContext;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import z80core.MemIoOps;
import z80core.Z80;
import z80core.Z80State;

import java.util.function.Function;

/**
 * ${FILE}
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2018
 * <p>
 *
 * TODO check interrupt handling vs halt
 */
public class Z80CoreWrapper implements Z80Provider {

    public static boolean verbose = Genesis.verbose || false;

    private Z80 z80Core;
    private Z80BusProvider z80BusProvider;
    private MemIoOps memIoOps;
    private Z80Disasm z80Disasm;

    private boolean resetState;
    private boolean busRequested;
    private boolean wasRunning;
    public static boolean activeInterrupt;

    private static Logger LOG = LogManager.getLogger(Z80CoreWrapper.class.getSimpleName());

    public static Z80CoreWrapper createInstance(BusProvider busProvider) {
        Z80CoreWrapper w = createInstance(busProvider, new Z80Memory(), null);
        return w;
    }

    public static Z80CoreWrapper createInstance(BusProvider busProvider, IMemory z80Memory, Z80State z80State) {
        Z80CoreWrapper w = new Z80CoreWrapper();
        w.z80BusProvider = new Z80BusProviderImpl(busProvider, w, z80Memory);
        w.memIoOps = createGenesisIo(w.z80BusProvider, z80Memory);

        w.z80Core = new Z80(w.memIoOps, null);
        if (z80State != null) {
            w.z80Core.setZ80State(z80State);
        }

        Z80MemContext memContext = Z80MemContext.createInstance(z80Memory);
        w.z80Disasm = new Z80Disasm(memContext, new Z80Decoder(memContext));
        return w;
    }

    //TEST
    public Z80CoreWrapper() {
    }

    @Override
    public void initialize() {
        reset();
        unrequestBus();
        wasRunning = false;
        LOG.info("Z80 init, reset: " + resetState + ", busReq: " + busRequested);
    }

    private boolean updateRunningFlag() {
        //TODO check why this breaks Z80 WAV PLAYER
        if (z80Core.isHalted()) {
            wasRunning = false;
            return false;
        }
        boolean nowRunning = isRunning();
        if (wasRunning != nowRunning) {
//            LOG.info("Z80: " + (nowRunning ? "ON" : "OFF"));
            wasRunning = nowRunning;
        }
        if (!nowRunning) {
            return false;
        }
        return true;
    }

    @Override
    public int executeInstruction() {
        if (!updateRunningFlag()) {
            return -1;
        }
        memIoOps.reset();
        try {
            printVerbose();
            z80Core.execute();
            if (verbose) {
                LOG.info("Z80State: " + toString(z80Core.getZ80State()));
            }
        } catch (Exception e) {
            LOG.error("z80 exception", e);
            LOG.error("Z80State: " + toString(z80Core.getZ80State()));
            LOG.error("Halting Z80");
            z80Core.setHalted(true);
        }
        return (int) (memIoOps.getTstates());
    }

    private static String toString(Z80State state) {
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

    @Override
    public void requestBus() {
        busRequested = true;
    }

    @Override
    public void unrequestBus() {
        busRequested = false;
    }

    @Override
    public boolean isBusRequested() {
        return busRequested;
    }

    //From the Z80UM.PDF document, a reset clears the interrupt enable, PC and
    //registers I and R, then sets interrupt status to mode 0.
    @Override
    public void reset() {
        //from Exodus
        z80Core.setRegSP(0xFFFF);
        z80Core.setRegAF(0xFFFF);
        z80Core.setRegPC(0);
        z80Core.setRegI(0);
        z80Core.setRegR(0);
        z80Core.setIFF1(false);
        z80Core.setIFF2(false);
        z80Core.setIM(Z80.IntMode.IM0);
        resetState = true;
    }

    @Override
    public boolean isReset() {
        return resetState;
    }

    @Override
    public void disableReset() {
        resetState = false;
    }

    @Override
    public boolean isRunning() {
        return !busRequested && !resetState;
    }

    @Override
    public boolean isHalted() {
        return z80Core.isHalted();
    }

    //If the Z80 has interrupts disabled when the frame interrupt is supposed
    //to occur, it will be missed, rather than made pending.
    @Override
    public boolean interrupt() {
        //TODO improve interrupt handling,
        //TODO needs to decide when an interrupt is taken by z80
//        boolean interruptEnable = z80Core.isIFF1();
//        if (interruptEnable) {
//            activeInterrupt = true;
//        }
//        return interruptEnable;
        activeInterrupt = true;
        return true;
    }

    @Override
    public int readMemory(int address) {
        return z80BusProvider.readMemory(address);
    }

    @Override
    public void writeMemory(int address, int data) {
        z80BusProvider.writeMemory(address, data);
        LogHelper.printLevel(LOG, Level.DEBUG, "Write Z80: {}, {}", address, data, verbose);
    }

    @Override
    public Z80BusProvider getZ80BusProvider() {
        return this.z80BusProvider;
    }

    @Override
    public void loadZ80State(Z80State z80State) {
        this.z80Core.setZ80State(z80State);
    }

    @Override
    public Z80State getZ80State() {
        return z80Core.getZ80State();
    }

    /**
     * Z80 for genesis doesnt do IO
     *
     * @return
     */
    public static MemIoOps createGenesisIo(Z80BusProvider z80BusProvider, IMemory z80Memory) {
//        The Z80 runs in interrupt mode 1, where an interrupt causes a RST 38h.
//        However, interrupt mode 0 can be used as well, since FFh will be read off the bus.

        MemIoOps memIoOps = new MemIoOps() {

            @Override
            public int peek8(int address) {
                this.interruptHandlingTime(3); //TODO for lack of a better method ...
                return z80BusProvider.readMemory(address);
            }

            @Override
            public void poke8(int address, int value) {
                this.interruptHandlingTime(3); //TODO for lack of a better method ...
                z80BusProvider.writeMemory(address, value);
            }

            @Override
            public int inPort(int port) {
                super.inPort(port);
                //TF4 calls this by mistake
                LOG.debug("inPort: {}", port);
                return 0xFF;
            }

            @Override
            public void outPort(int port, int value) {
                super.outPort(port, value);
                LOG.warn("outPort: " + port + ", data: " + value);
            }

            @Override
            public boolean isActiveINT() {
                boolean res = activeInterrupt;
                activeInterrupt = false;
                return res;
            }
        };
        memIoOps.setRam(z80Memory.getData());
        return memIoOps;
    }

    public static Function<DisassembledInstruction, String> disasmToString = d ->
            String.format("%08x   %12s   %s", d.getAddress(), d.getOpCode(), d.getMnemo());

    private void printVerbose() {
        if (verbose) {
            String str = disasmToString.apply(z80Disasm.disassemble(z80Core.getRegPC()));
            LOG.info(str);
        }
    }
}