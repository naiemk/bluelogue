package sample;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.paint.Color;
import org.jtransforms.fft.FloatFFT_1D;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

public class Controller implements Initializable {
    public static ScheduledThreadPoolExecutor worker = new ScheduledThreadPoolExecutor(4);
    private static final int CANVAS_HEIGHT = 200;
    private static final int WAVE_WIDTH = 400;
    private static final int SPEC_WIDTH = 400;
    private GraphicsContext graphicsWaveForm;
    private GraphicsContext graphicSpectogram;
    @FXML private Canvas spectogramCanvas;
    @FXML private Canvas waveFormCanvas;
    @FXML private TextArea dialog;
    @FXML private Button dialogBtn;

    private int waveX = WAVE_WIDTH;
    private int specX = SPEC_WIDTH;
    private int[] scale = new int[] {1};
    private CancellationToken calcellationToken = new CancellationToken(false);

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        graphicsWaveForm = waveFormCanvas.getGraphicsContext2D();
        graphicSpectogram = spectogramCanvas.getGraphicsContext2D();
        System.out.println("color set to black");
        System.out.println("draw rectangle");
        setUpDialog();
        listenAndPaintSpectogram();

    }

    @FXML
    protected void converse(ActionEvent event) {
        // Stop dialogue and start conversation conversation.
        worker.schedule(() -> new DialogueService(dialog.getText())
                .startSpeechToText(calcellationToken), 1, TimeUnit.SECONDS);
        dialogBtn.setDisable(true);
    }

    private void listenAndPaintSpectogram() {
        worker.schedule(() -> new Recorder(d -> {
            float[] samples = d.getSamples(); // Raw sound sample amplitudes
            float[] fft = new float[samples.length * 2]; // FFT of the sample amplitudes
            System.arraycopy(samples, 0, fft, 0, samples.length);
            FloatFFT_1D fftDo = new FloatFFT_1D(samples.length);
            fftDo.realForwardFull(fft); // Generate the FFT (both real and imaginary parts)

            double[] amps = new double[(samples.length+1)/2]; // Calculate FFT amplitudes

            // Normalize the FFT amplitudes
            double minamp = 0; // Min is always 0. We could technically not have this. Keeping for readability.
            double maxamp = 0;
            for(int f=0; f < amps.length; f++) {
                final double amp = Math.log(1+Math.sqrt(fft[f * 2] * fft[f * 2] + fft[f * 2 + 1] * fft[f * 2 + 1]));
                amps[f] = amp;
                minamp = Math.min(amp, minamp);
                maxamp = Math.max(amp, maxamp);
            }

            double scaleFactor = 0xFFFFFF / (maxamp - minamp);

            // Print one column of FFT
            final int x = addSpecX();
            for(int f=0; f < amps.length; f++) {
                final double h = CANVAS_HEIGHT - CANVAS_HEIGHT * f / amps.length;
                final double amp = amps[f] * scaleFactor;
                Platform.runLater(() -> {
                    double ratio = 2 * amp / 0xFFFFFF;
                    int b = (int)Math.max(0, 255*(1 - ratio));
                    int r = (int)Math.max(0, 255*(ratio - 1));
                    int g = 255 - b - r;
                    graphicSpectogram.setStroke(Color.rgb(r,g,b));
                    graphicSpectogram.strokeLine(x, h, x + 1, h);
                });
            }

            // Bucket samples for better display.
            double zoomOutRatio = 0.005;
            int zoomWidth = (int)(1 / zoomOutRatio);
            for (int i = 0; i < samples.length * zoomOutRatio; i++) {
                int index = i * zoomWidth;
                double sMax = IntStream.range(Math.max(0, index - zoomWidth), Math.min(index, samples.length))
                        .mapToDouble(j -> samples[j])
                        .max()
                        .orElse(0);
                double sMin = IntStream.range(Math.max(0, index - zoomWidth), Math.min(index, samples.length))
                        .mapToDouble(j -> samples[j])
                        .min()
                        .orElse(0);
                double yMin = sMin * CANVAS_HEIGHT * scale[0] + CANVAS_HEIGHT / 2;
                double yMax = sMax * CANVAS_HEIGHT * scale[0] + CANVAS_HEIGHT / 2;
                int waveX = addWaveX();
                Platform.runLater(() -> {
                    graphicsWaveForm.setStroke(Color.WHITE);
                    graphicsWaveForm.strokeLine(waveX, yMin, waveX, yMax);
                });
            }
        }).start(calcellationToken), 1, TimeUnit.SECONDS);

    }

    private void setUpDialog() {
        dialog.textProperty().setValue("#Paste dialogue here.\n #Use <keyword>:<Answer>");
    }

    private int addWaveX() {
        waveX ++;
        if (waveX > WAVE_WIDTH) {
            waveX = 0;
            Platform.runLater(() -> {
                graphicsWaveForm.setFill(Color.BLACK);
                graphicsWaveForm.fillRect(0, 0, 400, 200);
            });
        }

        return waveX;
    }

    private int addSpecX() {
        specX ++;
        if (specX > SPEC_WIDTH) {
            specX = 0;
            Platform.runLater(() -> {
                graphicSpectogram.setFill(Color.BLACK);
                graphicSpectogram.fillRect(0, 0, 400, 200);
            });
        }

        return specX;
    }
}
