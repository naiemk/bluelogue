package sample;

import javax.sound.sampled.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * @author naiemk
 */
public class Recorder {
    static final AudioFormat AUDIO_FORMAT = new AudioFormat(16000f, 16, 1, true, true);
    static final Supplier<TargetDataLine> microphoneLine = () -> {
        try {
            return AudioSystem.getTargetDataLine(AUDIO_FORMAT);
        } catch (LineUnavailableException e) {
            throw new RuntimeException("Error getting microphone line.", e);
        }
    };

    private Consumer<MicData> micreceiver;

    public Recorder(Consumer<MicData> micreceiver) {

        this.micreceiver = micreceiver;
    }

    public void start(CancellationToken cancel) {
        try {
            TargetDataLine microphone = microphoneLine.get();
            microphone.open(AUDIO_FORMAT);

            int numBytesRead;
            int CHUNK_SIZE = 1024;
            byte[] data = new byte[microphone.getBufferSize()];
            microphone.start();

            float[] samples = new float[AUDIO_FORMAT.getChannels() * CHUNK_SIZE];
            long[] transfer = new long[samples.length];

            while (!cancel.cancelled) {
                numBytesRead = microphone.read(data, 0, CHUNK_SIZE);
                samples = unpack(data, transfer, samples, numBytesRead, AUDIO_FORMAT);
                final float[] newSamples = samples;

                Stream.of(data).forEach(b -> micreceiver.accept(MicData.from(newSamples)));
            }

            microphone.stop();
            microphone.close();
        } catch (LineUnavailableException e) {
            e.printStackTrace();
        }
    }

    public static float[] unpack(
            byte[] bytes,
            long[] transfer,
            float[] samples,
            int bvalid,
            AudioFormat fmt
    ) {
        if(fmt.getEncoding() != AudioFormat.Encoding.PCM_SIGNED
                && fmt.getEncoding() != AudioFormat.Encoding.PCM_UNSIGNED) {

            return samples;
        }

        final int bitsPerSample = fmt.getSampleSizeInBits();
        final int bytesPerSample = bitsPerSample / 8;
        final int normalBytes = normalBytesFromBits(bitsPerSample);

        /*
         * not the most DRY way to do this but it's a bit more efficient.
         * otherwise there would either have to be 4 separate methods for
         * each combination of endianness/signedness or do it all in one
         * loop and check the format for each sample.
         *
         * a helper array (transfer) allows the logic to be split up
         * but without being too repetetive.
         *
         * here there are two loops converting bytes to raw long samples.
         * integral primitives in Java get sign extended when they are
         * promoted to a larger type so the & 0xffL mask keeps them intact.
         *
         */

        if(fmt.isBigEndian()) {
            for(int i = 0, k = 0, b; i < bvalid; i += normalBytes, k++) {
                transfer[k] = 0L;

                int least = i + normalBytes - 1;
                for(b = 0; b < normalBytes; b++) {
                    transfer[k] |= (bytes[least - b] & 0xffL) << (8 * b);
                }
            }
        } else {
            for(int i = 0, k = 0, b; i < bvalid; i += normalBytes, k++) {
                transfer[k] = 0L;

                for(b = 0; b < normalBytes; b++) {
                    transfer[k] |= (bytes[i + b] & 0xffL) << (8 * b);
                }
            }
        }

        final long fullScale = (long)Math.pow(2.0, bitsPerSample - 1);

        /*
         * the OR is not quite enough to convert,
         * the signage needs to be corrected.
         *
         */

        if(fmt.getEncoding() == AudioFormat.Encoding.PCM_SIGNED) {

            /*
             * if the samples were signed, they must be
             * extended to the 64-bit long.
             *
             * the arithmetic right shift in Java  will fill
             * the left bits with 1's if the MSB is set.
             *
             * so sign extend by first shifting left so that
             * if the sample is supposed to be negative,
             * it will shift the sign bit in to the 64-bit MSB
             * then shift back and fill with 1's.
             *
             * as an example, imagining these were 4-bit samples originally
             * and the destination is 8-bit, if we have a hypothetical
             * sample -5 that ought to be negative, the left shift looks
             * like this:
             *
             *     00001011
             *  <<  (8 - 4)
             *  ===========
             *     10110000
             *
             * (except the destination is 64-bit and the original
             * bit depth from the file could be anything.)
             *
             * and the right shift now fills with 1's:
             *
             *     10110000
             *  >>  (8 - 4)
             *  ===========
             *     11111011
             *
             */

            final long signShift = 64L - bitsPerSample;

            for(int i = 0; i < transfer.length; i++) {
                transfer[i] = (
                        (transfer[i] << signShift) >> signShift
                );
            }
        } else {

            /*
             * unsigned samples are easier since they
             * will be read correctly in to the long.
             *
             * so just sign them:
             * subtract 2^(bits - 1) so the center is 0.
             *
             */

            for(int i = 0; i < transfer.length; i++) {
                transfer[i] -= fullScale;
            }
        }

        /* finally normalize to range of -1.0f to 1.0f */

        for(int i = 0; i < transfer.length; i++) {
            samples[i] = (float)transfer[i] / (float)fullScale;
        }

        return samples;
    }

    public static int normalBytesFromBits(int bitsPerSample) {

        /*
         * some formats allow for bit depths in non-multiples of 8.
         * they will, however, typically pad so the samples are stored
         * that way. AIFF is one of these formats.
         *
         * so the expression:
         *
         *  bitsPerSample + 7 >> 3
         *
         * computes a division of 8 rounding up (for positive numbers).
         *
         * this is basically equivalent to:
         *
         *  (int)Math.ceil(bitsPerSample / 8.0)
         *
         */

        return bitsPerSample + 7 >> 3;
    }
}
