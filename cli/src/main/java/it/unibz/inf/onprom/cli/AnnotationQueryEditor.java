package it.unibz.inf.onprom.cli;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.swing.SwingTerminalFontConfiguration;
import com.googlecode.lanterna.terminal.swing.TerminalEmulatorColorConfiguration;
import com.googlecode.lanterna.terminal.swing.TerminalEmulatorPalette;
import it.unibz.inf.onprom.data.query.AnnotationQueries;
import it.unibz.inf.onprom.ui.utility.IOUtility;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Optional;

public class AnnotationQueryEditor {

    private final String initialContent;
    private final File oFile;

    public AnnotationQueryEditor(AnnotationQueries original, File outputFile) {
        this.initialContent = IOUtility.writeJSON(original);
        this.oFile = outputFile;
    }

    public void edit() {
        TerminalSize preferredSize = new TerminalSize(80, 25);
        SwingTerminalFontConfiguration fontConfig =
                SwingTerminalFontConfiguration.newInstance(new Font("Consolas", Font.PLAIN, 12));
        DefaultTerminalFactory factory = new DefaultTerminalFactory().setTerminalEmulatorFontConfiguration(fontConfig)
                .setTerminalEmulatorColorConfiguration(TerminalEmulatorColorConfiguration
                        .newInstance(TerminalEmulatorPalette.GNOME_TERMINAL));
        Screen screen;
        try {
            screen = factory.createScreen();
            screen.startScreen();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        MultiWindowTextGUI gui = new MultiWindowTextGUI(screen);

        BasicWindow window = new BasicWindow("Annotation Query Editor");

        Panel panel = new Panel();
        panel.setLayoutManager(new LinearLayout(Direction.VERTICAL));

        TextBox editor = new TextBox(
                preferredSize, initialContent,
                TextBox.Style.MULTI_LINE
        );

        editor.setVerticalFocusSwitching(false);
        panel.addComponent(editor);
        Panel buttons = getPanel(editor, window);
        panel.addComponent(buttons);
        window.setComponent(panel);
        gui.addWindowAndWait(window);
        try {
            screen.stopScreen();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private @NonNull Panel getPanel(TextBox editor, BasicWindow window) {
        Panel buttons = new Panel();
        Button save = new Button("Save", () -> {
            try {
                Optional<AnnotationQueries> optionalAnnotationQueries =
                        IOUtility.inputJSON(editor.getText(), AnnotationQueries.class);
                if(optionalAnnotationQueries.isPresent()) {
                    IOUtility.exportJSON(oFile, optionalAnnotationQueries);
                    window.close();
                } else {
                    window.setComponent(new Label("Invalid Annotation Queries JSON! See log for details."));
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        Button cancel = new Button("Cancel", window::close);
        buttons.addComponent(save);
        buttons.addComponent(cancel);
        return buttons;
    }
}