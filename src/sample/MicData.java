package sample;

/**
 * Created by yeganehn on 9/29/16.
 */
public class MicData {
    private float[] samples;
    public static MicData from(float[] b) {
        MicData m = new MicData();
        m.samples = b;
        return m;
    }

    public float[] getSamples() {
        return samples;
    }
}
