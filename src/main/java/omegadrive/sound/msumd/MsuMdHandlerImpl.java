package omegadrive.sound.msumd;

import com.google.common.io.Files;
import omegadrive.util.LogHelper;
import omegadrive.util.Size;
import omegadrive.util.SoundUtil;
import omegadrive.util.Util;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.digitalmediaserver.cuelib.CueSheet;
import org.digitalmediaserver.cuelib.TrackData;

import javax.sound.sampled.*;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * MsuMdHandlerImpl
 * TODO fadeOut
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2020
 */
public class MsuMdHandlerImpl implements MsuMdHandler {

    private static final Logger LOG = LogManager.getLogger(MsuMdHandlerImpl.class.getSimpleName());
    private static final boolean verbose = false;

    private MsuCommandArg commandArg = new MsuCommandArg();
    private int clock = 0;
    private boolean init;
    private volatile Clip clip;
    private volatile long clipPosition;
    private boolean paused;
    private volatile byte[] buffer = new byte[0];
    private AtomicReference<LineListener> lineListenerRef = new AtomicReference<>();
    private RandomAccessFile binFile;
    private TrackDataHolder[] trackDataHolders = new TrackDataHolder[CueFileParser.MAX_TRACKS];

    protected void initTrackData(CueSheet cueSheet, long binLen) {
        List<TrackData> trackDataList = cueSheet.getAllTrackData();
        Arrays.fill(trackDataHolders, NO_TRACK);
        for (TrackData td : trackDataList) {
            TrackDataHolder h = new TrackDataHolder();
            h.type = CueFileDataType.getFileType(td.getParent().getFileType());
            switch (h.type) {
                case BINARY:
                    int numBytes = 0;
                    int startFrame = td.getFirstIndex().getPosition().getTotalFrames();
                    if (trackDataList.size() == td.getNumber()) {
                        numBytes = (int) binLen - (startFrame * CueFileParser.SECTOR_SIZE_BYTES);
                    } else {
                        TrackData trackDataNext = trackDataList.get(td.getNumber());
                        int endFrame = trackDataNext.getFirstIndex().getPosition().getTotalFrames();
                        numBytes = (endFrame - startFrame) * CueFileParser.SECTOR_SIZE_BYTES;
                    }
                    h.numBytes = Optional.of(numBytes);
                    h.startFrame = Optional.of(startFrame);
                    break;
                case WAVE:
                    h.waveFile = Optional.ofNullable(cueSheet.getFile().resolveSibling(td.getParent().getFile()).toFile());
                    break;
            }
            trackDataHolders[td.getNumber()] = h;
        }
    }


    private MsuMdHandlerImpl(CueSheet cueSheet, RandomAccessFile binFile) {
        this.binFile = binFile;
        LOG.info("Enabling MSU-MD handling, using cue sheet: {}", cueSheet.getFile().toAbsolutePath());
    }

    public static MsuMdHandler createInstance(Path romPath) {
        if (romPath == null) {
            return NO_OP_HANDLER;
        }
        CueSheet cueSheet = MsuMdHandlerImpl.initCueSheet(romPath);
        if (cueSheet == null) {
            LOG.info("Disabling MSU-MD handling, unable to find CUE file.");
            return NO_OP_HANDLER;
        }
        RandomAccessFile binFile = MsuMdHandlerImpl.initBinFile(romPath, cueSheet);
        if (binFile == null) {
            LOG.error("Disabling MSU-MD handling, unable to find BIN file");
            return NO_OP_HANDLER;
        }
        long binLen = 0;
        try {
            if ((binLen = binFile.length()) == 0) {
                throw new Exception("Zero length file: " + binFile.toString());
            }
        } catch (Exception e) {
            LOG.error("Disabling MSU-MD handling, unable to find BIN file");
            return NO_OP_HANDLER;
        }
        MsuMdHandlerImpl h = new MsuMdHandlerImpl(cueSheet, binFile);
        h.initTrackData(cueSheet, binLen);
        return h;
    }

    private int handleFirstRead() {
        init = true;
        LogHelper.printLevel(LOG, Level.INFO, "Read MCD_STATUS: {}", MCD_STATE.INIT, verbose);
        return MCD_STATE.INIT.ordinal();
    }

    private static CueSheet initCueSheet(Path romPath) {
        String romName = romPath.getFileName().toString();
        String cueFileName = romName.replace("." + Files.getFileExtension(romName), ".cue");
        return CueFileParser.parse(romPath.resolveSibling(cueFileName));
    }

    private static RandomAccessFile initBinFile(Path romPath, CueSheet sheet) {
        RandomAccessFile binFile = null;
        try {
            Path binPath = romPath.resolveSibling(sheet.getFileData().get(0).getFile());
            binFile = new RandomAccessFile(binPath.toFile(), "r");
        } catch (Exception e) {
            LOG.error(e);
        }
        return binFile;
    }

    private volatile boolean busy = false;
    private int lastPlayed = -1;

    public void setBusy(boolean busy) {
        LogHelper.printLevel(LOG, Level.INFO, "Busy: {} -> {}", this.busy ? 1 : 0, busy ? 1 : 0, verbose);
        this.busy = busy;
    }

    @Override
    public void handleMsuMdWrite(int address, int data, Size size) {
        //write to sub-cpu memory/wram, ignore
        if (address >= MCD_WRAM_START && address <= MCD_WRAM_END) {
            return;
        }
        switch (size) {
            case BYTE:
                handleMsuMdWriteByte(address, data);
                break;
            case WORD:
                handleMsuMdWriteByte(address, data >> 8);
                handleMsuMdWriteByte(address + 1, data & 0xFF);
                break;
            case LONG:
                handleMsuMdWriteByte(address, (data >> 24) & 0xFF);
                handleMsuMdWriteByte(address + 1, (data >> 16) & 0xFF);
                handleMsuMdWriteByte(address + 2, (data >> 8) & 0xFF);
                handleMsuMdWriteByte(address + 3, data & 0xFF);
                break;
        }
    }

    private void handleMsuMdWriteByte(int address, int data) {
        switch (address) {
            case CLOCK_ADDR:
                LogHelper.printLevel(LOG, Level.INFO, "Cmd clock: {} -> {}", clock, data, verbose);
                processCommand(commandArg);
                clock = data;
                break;
            case CMD_ADDR:
                commandArg.command = MsuCommand.getMsuCommand(data);
                LogHelper.printLevel(LOG, Level.INFO, "Cmd: {}, arg {}", commandArg.command, commandArg.arg, verbose);
                break;
            case CMD_ARG_ADDR:
                commandArg.arg = data;
                LogHelper.printLevel(LOG, Level.INFO, "Cmd Arg: {}, arg {}", commandArg.command, commandArg.arg, verbose);
                break;
            default:
                handleIgnoredMcdWrite(address, data);
                break;
        }
    }

    @Override
    public int handleMsuMdRead(int address, Size size) {
        switch (address) {
            case MCD_STATUS_ADDR:
                if (!init) { //first read needs to return INIT
                    return handleFirstRead();
                }
                MCD_STATE m = busy
//                        || (clip != null && clip.isRunning())
                        ? MCD_STATE.CMD_BUSY : MCD_STATE.READY;
                LogHelper.printLevel(LOG, Level.INFO, "Read MCD_STATUS: {}, busy: " + busy, m, verbose);
                return m.ordinal();
            case CLOCK_ADDR:
                LogHelper.printLevel(LOG, Level.INFO, "Read CLOCK_ADDR: {}", clock, verbose);
                return clock;
            default:
                return handleIgnoredMcdRead(address, size);
        }
    }

    private void handleIgnoredMcdWrite(int address, int data) {
        switch (address) {
            case MCD_GATE_ARRAY_START:
                LogHelper.printLevel(LOG, Level.INFO, "Write MCD_GATE_ARRAY_START: {}", data, verbose);
                break;
            case MCD_MMOD:
                LogHelper.printLevel(LOG, Level.INFO, "Write MCD_MMOD: {}", data, verbose);
                break;
            case MCD_COMF:
                LogHelper.printLevel(LOG, Level.INFO, "Write MCD_COMF: {}", data, verbose);
                break;
            case MCD_MEMWP:
                LogHelper.printLevel(LOG, Level.INFO, "Write MCD_MEMWP: {}", data, verbose);
                break;
            default:
                LOG.error("Unexpected bus write: {}, data {} {}",
                        Long.toHexString(address), Long.toHexString(data), Size.BYTE);
                break;
        }
    }

    private int handleIgnoredMcdRead(int address, Size size) {
        switch (address) {
            case MCD_GATE_ARRAY_START:
                LogHelper.printLevel(LOG, Level.INFO, "Read MCD_GATE_ARRAY_START: {}", 0xFF, verbose);
                return (int) size.getMask(); //ignore
            default:
                LOG.warn("Unexpected MegaCD address range read at: {}, {}",
                        Long.toHexString(address), size);
                return (int) size.getMask();
        }
    }

    private void processCommand(MsuCommandArg commandArg) {
        int arg = commandArg.arg;
        Runnable r = null;
        LogHelper.printLevel(LOG, Level.INFO, "{} track: {}", commandArg.command, arg, verbose);
        switch (commandArg.command) {
            case PLAY:
                r = playTrack(arg, false);
                break;
            case PLAY_LOOP:
                r = playTrack(arg, true);
                break;
            case PAUSE:
                r = pauseTrack(arg / 75d);
                break;
            case RESUME:
                r = resumeTrack();
                break;
            case VOL:
                r = volumeTrack(arg);
                break;
            default:
                LOG.warn("Unknown command: {}", commandArg.command);
        }
        if (r != null) {
            Util.executorService.submit(r);
        }
    }

    private Runnable volumeTrack(int val) {
        return () -> {
            if (clip != null) {
                FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                double gainAbs = Math.max(val, 0.1) / 255.0; //(0;1]
                float gain_dB = (float) (Math.log(gainAbs) / Math.log(10.0) * 20.0);
                float prevDb = gain.getValue();
                //0.0db is the line's normal gain (baseline), any value < 0xFF attenuates the volume
                gain.setValue(gain_dB);
                LOG.info("Volume: {}, gain_db changed from: {}, to: {}", val, prevDb, gain_dB);
            }
        };
    }

    private void stopTrackInternal(boolean busy) {
        SoundUtil.close(clip);
        clipPosition = 0;
        if (clip != null) {
            clip.removeLineListener(lineListenerRef.getAndSet(null));
        }
        setBusy(busy);
        LogHelper.printLevel(LOG, Level.INFO, "Track stopped", verbose);
    }

    private Runnable pauseTrack(final double fadeMs) {
        return () -> {
            if (clip != null) {
                clipPosition = clip.getMicrosecondPosition();
                clip.stop();
            }
            paused = true;
        };
    }

    private Runnable resumeTrack() {
        return () -> {
            startClipInternal(clip);
            paused = false;
        };
    }

    private Runnable playTrack(final int track, boolean loop) {
        //TODO HACK
        if (lastPlayed == track && track == 20) { //sonic 1 hack
            LOG.warn("Trying to play again track: {}, ignoring", track);
            return () -> {
            };
        }
        lastPlayed = track;
        setBusy(true);
        //TODO HACK
        return () -> {
            try {
                TrackDataHolder h = trackDataHolders[track];
                stopTrackInternal(true);
                clip = AudioSystem.getClip();
                lineListenerRef.set(this::handleClipEvent);
                clip.addLineListener(lineListenerRef.get());
                switch (h.type) {
                    case WAVE:
                    case OGG:
                        AudioInputStream ais = AudioSystem.getAudioInputStream(h.waveFile.get());
                        AudioInputStream dataIn = AudioSystem.getAudioInputStream(CDDA_FORMAT, ais);
                        clip.open(dataIn);
                        break;
                    case BINARY:
                        prepareBuffer(h);
                        clip.open(CDDA_FORMAT, buffer, 0, h.numBytes.get());
                        break;
                    default:
                        LOG.error("Unable to parse track {}, type: {}", track, h.type);
                        return;
                }
                clip.loop(loop ? Clip.LOOP_CONTINUOUSLY : 0);
                if (!paused) {
                    startClipInternal(clip);
                } else {
                    clipPosition = 0;
                }
                LogHelper.printLevel(LOG, Level.INFO, "Track started: {}", track, verbose);
            } catch (Exception e) {
                LOG.error(e);
                e.printStackTrace();
            } finally {
                setBusy(false);
            }
        };
    }

    @Override
    public void close() {
        stopTrackInternal(false);
        LOG.info("Closing");
    }

    private void handleClipEvent(LineEvent event) {
        LogHelper.printLevel(LOG, Level.INFO, "Clip event: {}", event.getType(), verbose);
    }

    private void startClipInternal(Clip clip) {
        if (clip == null) {
            return;
        }
        if (clipPosition > 0) {
            clip.setMicrosecondPosition(clipPosition);
        }
        clip.start();
    }

    enum MCD_STATE {READY, INIT, CMD_BUSY}

    private void prepareBuffer(TrackDataHolder h) {
        int numBytes = h.numBytes.get();
        if (buffer.length < numBytes) {
            buffer = new byte[numBytes];
        }
        Arrays.fill(buffer, (byte) 0);
        try {
            binFile.seek(h.startFrame.get() * CueFileParser.SECTOR_SIZE_BYTES);
            binFile.readFully(buffer, 0, numBytes);
        } catch (IOException e) {
            LOG.error(e);
        }
    }
}