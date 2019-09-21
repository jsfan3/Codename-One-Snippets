package net.informaticalibera.tests;

import com.codename1.io.FileSystemStorage;
import static com.codename1.ui.CN.*;
import com.codename1.ui.Display;
import com.codename1.ui.Form;
import com.codename1.ui.Dialog;
import com.codename1.ui.Label;
import com.codename1.ui.plaf.UIManager;
import com.codename1.ui.util.Resources;
import com.codename1.io.Log;
import com.codename1.ui.Toolbar;
import java.io.IOException;
import com.codename1.ui.layouts.BoxLayout;
import com.codename1.io.NetworkEvent;
import com.codename1.io.Util;
import com.codename1.ui.Button;
import com.codename1.ui.CN;
import com.codename1.ui.EncodedImage;
import com.codename1.ui.events.ActionEvent;
import com.codename1.ui.events.ActionListener;
import com.codename1.ui.geom.Dimension;
import com.codename1.ui.plaf.Style;
import com.codename1.ui.util.ImageIO;
import com.codename1.util.EasyThread;

/**
 * This file was generated by <a href="https://www.codenameone.com/">Codename
 * One</a> for the purpose of building native mobile applications using Java.
 */
public class TestCapture {

    private Form current;
    private Resources theme;
    private final EasyThread easyThread = EasyThread.start("SaveAndShowCapturedImages");

    public void init(Object context) {
        // use two network threads instead of one
        updateNetworkThreadCount(2);

        theme = UIManager.initFirstTheme("/theme");

        // Enable Toolbar on all Forms by default
        Toolbar.setGlobalToolbar(true);

        // Pro only feature
        Log.bindCrashProtection(true);

        addNetworkErrorListener(err -> {
            // prevent the event from propagating
            err.consume();
            if (err.getError() != null) {
                Log.e(err.getError());
            }
            Log.sendLogAsync();
            Dialog.show("Connection Error", "There was a networking error in the connection to " + err.getConnectionRequest().getUrl(), "OK", null);
        });
    }

    public void start() {
        if (current != null) {
            current.show();
            return;
        }
        Form hi = new Form("Test Capture", BoxLayout.y());
        Button testCaptureBtn = new Button("Test Capture, Scale and Save");
        Label label = new Label("") {
            public Dimension calcPreferredSize() {
                Dimension dim = super.calcPreferredSize();
                dim.setHeight(CN.convertToPixels(50, false));
                return dim;
            }
        };
        label.setShowEvenIfBlank(true);
        hi.addAll(testCaptureBtn, label);
        hi.show();

        testCaptureBtn.addActionListener(l -> {
            Display.getInstance().capturePhoto(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent ev) {
                    if (ev != null && ev.getSource() != null) {
                        // in this very simple case, an EasyThread is not necessary, however it could be useful in more complex cases
                        // note that it's important to reuse the same instance of EasyThread, see: https://stackoverflow.com/a/57984359
                        easyThread.run(() -> {
                            try {
                                String tempFile = (String) ev.getSource();
                                // checking the mimetype has no impact on RAM, it's to ensure that we received a valid Image
                                if ("image/jpg".equals(Util.guessMimeType(tempFile)) || "image/jpeg".equals(Util.guessMimeType(tempFile))) {
                                    String outputFile = getRandomUniqueFilePath("jpg");
                                    // "Util.copy" before "saveAndKeepAspect" is necessary because "saveAndKeepAspect", in this case, does nothing if the temp image is smaller than 1000x1000
                                    Util.copy(FileSystemStorage.getInstance().openInputStream(tempFile), FileSystemStorage.getInstance().openOutputStream(outputFile));
                                    // saveAndKeepAspect doesn't need to load the image into RAM, see: https://stackoverflow.com/a/57984359
                                    // a photo that is not larger than 1000x1000 (1MP) has a good trade-off between quality and memory, it uses a lot less RAM than the original photos (for example at 12MP)
                                    ImageIO.getImageIO().saveAndKeepAspect(tempFile, outputFile, ImageIO.FORMAT_JPEG, 1000, 1000, 0.9f, true, false);
                                    Log.p("Scaled photo saved to: " + outputFile, Log.DEBUG);
                                    // EncodedImages are better than Images about RAM usage, see the Javadocs
                                    EncodedImage encodedImg = EncodedImage.create(FileSystemStorage.getInstance().openInputStream(outputFile));
                                    CN.callSerially(() -> {
                                        // to do any GUI manipulation, we need to do that in the EDT, that's why this callSerially
                                        label.getAllStyles().setBackgroundType(Style.BACKGROUND_IMAGE_SCALED_FIT);
                                        label.getAllStyles().setBgImage(encodedImg);
                                        label.repaint();
                                        Log.p("Scaled photo shown fitted inside a Label which height is 5 cm", Log.DEBUG);
                                    });
                                } else {
                                    // this should not happen
                                    Log.p("Invalid photo received from the camera", Log.WARNING);
                                }
                            } catch (IOException ex) {
                                Log.e(ex);
                                Log.sendLogAsync();
                            }
                        });
                    }
                }
            });
        });
    }

    /**
     * Returns an unique (not used) file path in the app home path of
     * FileSystemStorage, with the given extension; if the extension doesn't
     * start with a dot ".", it will be added automatically.
     *
     * @param extension it can be null to don't add any extension
     * @return
     */
    public static String getRandomUniqueFilePath(String extension) {
        long timeStamp = System.currentTimeMillis();
        if (extension != null && !extension.startsWith(".")) {
            extension = "." + extension;
        }
        String randomPath;
        if (extension != null) {
            randomPath = getAppHomePath() + timeStamp + extension;
            // check that the randomPath is not used
            while (FileSystemStorage.getInstance().exists(randomPath)) {
                timeStamp++;
                randomPath = getAppHomePath() + timeStamp + extension;
            }
        } else {
            randomPath = getAppHomePath() + timeStamp;
            // check that the randomPath is not used
            while (FileSystemStorage.getInstance().exists(randomPath)) {
                timeStamp++;
                randomPath = getAppHomePath() + timeStamp;
            }
        }
        return randomPath;
    }

    public void stop() {
        current = getCurrentForm();
        if (current instanceof Dialog) {
            ((Dialog) current).dispose();
            current = getCurrentForm();
        }
    }

    public void destroy() {
    }

}
