package sample;

import com.ibm.watson.developer_cloud.http.HttpMediaType;
import com.ibm.watson.developer_cloud.speech_to_text.v1.SpeechToText;
import com.ibm.watson.developer_cloud.speech_to_text.v1.model.RecognizeOptions;
import com.ibm.watson.developer_cloud.speech_to_text.v1.model.SpeechResults;
import com.ibm.watson.developer_cloud.speech_to_text.v1.websocket.BaseRecognizeCallback;
import com.ibm.watson.developer_cloud.text_to_speech.v1.TextToSpeech;
import com.ibm.watson.developer_cloud.text_to_speech.v1.model.*;
import com.ibm.watson.developer_cloud.text_to_speech.v1.util.WaveUtils;
import org.apache.commons.io.IOUtils;

import javax.sound.midi.SysexMessage;
import javax.sound.sampled.*;
import javax.sound.sampled.AudioFormat;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A dialogue service.
 *
 * @author naiemk
 */
public class DialogueService {
    public static final String BLUE_MIX_STT_UN = "BLUEMIX_SPEACH_TO_TEXT_UN";
    public static final String BLUE_MIX_STT_PW = "BLUEMIX_SPEACH_TO_TEXT_PW";

    public static final String BLUE_MIX_TTS_UN = "BLUEMIX_SPEACH_TO_TEXT_UN";
    public static final String BLUE_MIX_TTS_PW = "BLUEMIX_SPEACH_TO_TEXT_PW";

    private final List<Voice> voices;
    private SpeechToText speachToText;
    private TextToSpeech textToSpeech;

    private Map<String, String> keywords;

    public DialogueService(String dialog) {
        processDialogue(dialog);
        speachToText = new SpeechToText();
        speachToText.setUsernameAndPassword(System.getenv(BLUE_MIX_STT_UN), System.getenv(BLUE_MIX_STT_PW));

        textToSpeech = new TextToSpeech();
        textToSpeech.setUsernameAndPassword(System.getenv(BLUE_MIX_TTS_UN), System.getProperty(BLUE_MIX_TTS_PW));
        voices = textToSpeech.getVoices().execute();
        playOutLoad("Let us start the game!");
    }

    public void startSpeechToText(CancellationToken cancellation) {

        RecognizeOptions options = new RecognizeOptions.Builder()
                .continuous(true)
                .interimResults(true)
                .contentType(HttpMediaType.AUDIO_RAW + "; rate=" + 16000)
                .keywords(keywords.keySet().stream().toArray(String[]::new))
                .keywordsThreshold(0.4)
                .build();

        try (TargetDataLine line = Recorder.microphoneLine.get()) {
            line.open(Recorder.AUDIO_FORMAT);
            line.start();
            AudioInputStream audio = new AudioInputStream(line);
            speachToText.recognizeUsingWebSocket(audio, options, new BaseRecognizeCallback() {
                @Override
                public void onTranscription(SpeechResults speechResults) {
                    playResponse(speechResults);
                }
            });

            while (!cancellation.cancelled) {
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (LineUnavailableException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private void playResponse(SpeechResults speechResults) {
        if (speechResults.getResults() != null) {
            List<String> kwds = speechResults.getResults().stream()
                    .filter(r -> null != r.getKeywordsResult())
                    .flatMap(t -> t.getKeywordsResult().keySet().stream())
                    .collect(Collectors.toList());

            speechResults.getResults().stream().forEach(r -> r.getAlternatives().stream()
                    .forEach(a -> System.out.print(a.getTranscript())));
            System.out.println("$" + speechResults.isFinal());

            // Play result for the first keyword;
            if (kwds.size() > 0) {
                String reply = keywords.get(kwds.get(0));
                System.out.println(">>>" + reply);
                playOutLoad(reply);
            }
        }
    }

    private void playOutLoad(String reply) {
        Controller.worker.execute(() -> {
            try (InputStream stream = textToSpeech.synthesize(reply,
                        Voice.EN_LISA,
                        com.ibm.watson.developer_cloud.text_to_speech.v1.model.AudioFormat.WAV)
                        .execute()) {
                System.out.println("PLAYING: " + reply);
//                ByteArrayInputStream bas = new ByteArrayInputStream(IOUtils.toByteArray(stream));
                Clip clip = AudioSystem.getClip();
                AudioInputStream audio = AudioSystem.getAudioInputStream(WaveUtils.reWriteWaveHeader(stream));
                clip.open(audio);
                clip.start();
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } catch (LineUnavailableException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (UnsupportedAudioFileException e) {
                e.printStackTrace();
            }
        });
    }

    private void processDialogue(String dialog) {
        List<String> lines = Stream.of(dialog.split("\n"))
                .map(l -> l.trim())
                .filter(l -> !l.startsWith("#") && l.contains(":"))
                .collect(Collectors.toList());

        keywords = lines.stream()
                .map(l -> l.split(":"))
                .collect(Collectors.groupingBy(p -> p[0].trim().toLowerCase(), Collectors.toList()))
                .entrySet()
                .stream()
                .collect(Collectors.toMap(e -> e.getKey(),
                        e -> e.getValue().stream().map(p -> p[1]).findFirst().get()));
    }
}
