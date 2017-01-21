package sample;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.stream.Stream;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception{
        if (Stream.of(DialogueService.BLUE_MIX_STT_UN,
                        DialogueService.BLUE_MIX_STT_PW,
                        DialogueService.BLUE_MIX_TTS_UN,
                        DialogueService.BLUE_MIX_TTS_PW)
                .filter(Main::validateEnv)
                .count() > 0) {
            throw new RuntimeException("Some environment variable is not defined.");
        }

        Parent root = FXMLLoader.load(getClass().getResource("sample.fxml"));
        primaryStage.setTitle("Bot Ready Dialog System");
        primaryStage.setScene(new Scene(root, 600, 1600));
        primaryStage.show();
    }

    @Override
    public void stop() throws Exception {
        System.out.println("Shutting Down.");
        Controller.worker.shutdownNow();
        Platform.exit();
        super.stop();
        System.exit(0);
    }

    private static boolean validateEnv(String env) {
        String value = System.getenv(env);
        if (null == value || "".equals(value)) {
            System.out.println("Make sure to set environment variable: " + env);
            return true;
        }

        return false;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
