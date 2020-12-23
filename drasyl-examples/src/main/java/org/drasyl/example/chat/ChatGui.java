/*
 * Copyright (c) 2020.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.drasyl.example.chat;

import org.drasyl.DrasylConfig;
import org.drasyl.DrasylException;
import org.drasyl.DrasylNode;
import org.drasyl.event.Event;
import org.drasyl.event.MessageEvent;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeOfflineEvent;
import org.drasyl.event.NodeOnlineEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.event.PeerEvent;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;

import static javax.swing.JOptionPane.ERROR_MESSAGE;
import static javax.swing.WindowConstants.EXIT_ON_CLOSE;

/**
 * This is an Example of a Chat Application running on the drasyl Overlay Network. It allows you to
 * send Text Messages to other drasyl Nodes running this Chat Application.
 */
public class ChatGui {
    private final JFrame frame = new JFrame();
    private final JTextFieldWithPlaceholder recipientField = new JTextFieldWithPlaceholder(10, "Enter Recipient");
    private final JTextField messageField = new JTextFieldWithPlaceholder("Enter Message");
    private final JTextArea messagesArea = new JTextArea(30, 70);
    private final DrasylConfig config;
    private DrasylNode node;

    public ChatGui(final DrasylConfig config) {
        this.config = config;

        // initial fields state
        recipientField.setEditable(false);
        messageField.setEditable(false);
        messagesArea.setEditable(false);

        // output
        frame.getContentPane().add(new JScrollPane(messagesArea), BorderLayout.CENTER);

        // input
        final JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(recipientField, BorderLayout.WEST);
        panel.add(messageField, BorderLayout.CENTER);
        frame.getContentPane().add(panel, BorderLayout.SOUTH);

        // send message on enter
        messageField.addActionListener(event -> {
            if (node != null) {
                final String recipient = recipientField.getText().trim();
                if (!recipient.isBlank()) {
                    messageField.setEditable(false);
                    node.send(recipient, messageField.getText()).whenComplete((result, e) -> {
                        if (e != null) {
                            JOptionPane.showMessageDialog(frame, e, "Error", ERROR_MESSAGE);
                        }
                        messagesArea.append(" To " + recipient + ": " + messageField.getText() + "\n");
                        messageField.setText("");
                        messageField.setEditable(true);
                        messagesArea.setCaretPosition(messagesArea.getDocument().getLength());
                    });
                }
            }
        });

        // shutdown node on window close
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(final WindowEvent e) {
                if (node != null) {
                    node.shutdown();
                }
            }
        });

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(EXIT_ON_CLOSE);
        frame.setVisible(true);
    }

    private void run() throws DrasylException {
        node = new DrasylNode(config) {
            @Override
            public void onEvent(final Event event) {
                if (event instanceof NodeUpEvent) {
                    messagesArea.append("drasyl Node started. Only communication with direct connected peers possible. Connecting to super peer...\n");
                    recipientField.setEditable(true);
                    messageField.setEditable(true);
                }
                if (event instanceof NodeDownEvent) {
                    messagesArea.append("drasyl Node is shutting down. No more communication possible.\n");
                    recipientField.setEditable(false);
                    messageField.setEditable(false);
                }
                else if (event instanceof NodeOnlineEvent) {
                    messagesArea.append("drasyl Node is connected to super peer. Relayed communication and discovery available.\n");
                }
                else if (event instanceof NodeOfflineEvent) {
                    messagesArea.append("drasyl Node lost connection to super peer. Relayed communication and discovery not available.\n");
                }
                else if (event instanceof MessageEvent) {
                    messagesArea.append(" From " + ((MessageEvent) event).getSender() + ": " + ((MessageEvent) event).getPayload() + "\n");
                    Toolkit.getDefaultToolkit().beep();
                }
                else if (event instanceof PeerEvent) {
                    messagesArea.append(event + "\n");
                }
                messagesArea.setCaretPosition(messagesArea.getDocument().getLength());
            }
        };
        frame.setTitle("Chat: " + node.identity().getPublicKey().toString());
        messagesArea.append("*******************************************************************************************************\n");
        messagesArea.append("This is an Example of a Chat Application running on the drasyl Overlay Network.\n");
        messagesArea.append("It allows you to send Text Messages to other drasyl Nodes running this Chat Application.\n");
        messagesArea.append("Your address is " + node.identity().getPublicKey() + "\n");
        messagesArea.append("*******************************************************************************************************\n");

        node.start();
    }

    public static void main(final String[] args) throws Exception {
        final DrasylConfig config;
        if (args.length == 1) {
            config = DrasylConfig.parseFile(new File(args[0]));
        }
        else {
            config = new DrasylConfig();
        }

        final ChatGui gui = new ChatGui(config);
        gui.run();
    }
}
