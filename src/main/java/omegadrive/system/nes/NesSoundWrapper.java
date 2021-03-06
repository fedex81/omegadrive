package omegadrive.system.nes;

import com.grapeshot.halfnes.audio.AudioOutInterface;
import omegadrive.sound.fm.ExternalAudioProvider;
import omegadrive.util.RegionDetector;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.sound.sampled.AudioFormat;

/**
 * NesSoundWrapper
 * <p>
 * Federico Berti
 * <p>
 * Copyright 2020
 */
public class NesSoundWrapper extends ExternalAudioProvider implements AudioOutInterface {

    private static final Logger LOG = LogManager.getLogger(NesSoundWrapper.class.getSimpleName());

    private static final double VOLUME = 13107 / 16384.;

    public NesSoundWrapper(RegionDetector.Region region, AudioFormat audioFormat) {
        super(region, audioFormat);
        start();
    }

    @Override
    public void outputSample(int sample) {
        sample *= VOLUME;
        sample = sample < Short.MIN_VALUE ? Short.MIN_VALUE :
                (sample > Short.MAX_VALUE ? Short.MAX_VALUE : sample);
        addMonoSample(sample);
    }

    @Override
    public void flushFrame(boolean waitIfBufferFull) {
        //DO NOTHING
//        LOG.info("flush, waitIfFull: {}, samples: {}" ,waitIfBufferFull, queueLen.get());
    }

    @Override
    public boolean bufferHasLessThan(int samples) {
//        LOG.info("bufferHasLessThan: {}, actual: {}", samples, queueLen.get());
        return queueLen.get() < samples;
    }

    @Override
    public void pause() {
        stop();
    }

    @Override
    public void resume() {
        start();
    }

    @Override
    public void destroy() {
        reset();
    }
}
