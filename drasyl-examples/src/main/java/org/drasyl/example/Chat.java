package org.drasyl.example;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.drasyl.DrasylException;
import org.drasyl.DrasylNode;
import org.drasyl.event.Event;
import org.drasyl.identity.Identity;
import org.drasyl.util.Pair;

import java.io.File;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;

/**
 * This is an Example of a Chat Application running on the drasyl Overlay Network. It allows you to
 * send Text Messages to other drasyl Nodes running this Chat Application.
 */
public class Chat {
    public static void main(String[] args) throws DrasylException, InterruptedException {
        Config config;
        if (args.length == 1) {
            config = ConfigFactory.parseFile(new File(args[0])).withFallback(ConfigFactory.load());
        }
        else {
            config = ConfigFactory.load();
        }

        CompletableFuture<Void> online = new CompletableFuture<>();
        DrasylNode node = new DrasylNode(config) {
            @Override
            public void onEvent(Event event) {
                switch (event.getCode()) {
                    case EVENT_MESSAGE:
                        Pair<Identity, byte[]> message = event.getMessage();
                        System.out.println("From " + message.first().getId() + ": " + new String(message.second()));
                        break;
                    case EVENT_NODE_ONLINE:
                        online.complete(null);
                        System.out.println("Your Address is: " + event.getNode().getAddress().getId());
                        break;
                    default:
                        System.out.println(event);
                }
            }
        };
        node.start().join();
        online.join();

        System.out.println();
        System.out.println("****************************************************************************************");
        System.out.println("This is an Example of a Chat Application running on the drasyl Overlay Network.");
        System.out.println("It allows you to send Text Messages to other drasyl Nodes running this Chat Application.");
        System.out.println("****************************************************************************************");
        System.out.println();

        String recipient = "";
        Scanner scanner = new Scanner(System.in);
        while (true) {
            try {
                // prompt for recipient
                System.out.print("Recipient [" + recipient + "]? ");
                recipient = scanner.nextLine();

                // prompt for message
                System.out.print("Message? ");
                String message = scanner.nextLine();

                node.send(recipient, message);
                System.out.println("To " + recipient + ": " + message);
            }
            catch (IllegalArgumentException e) {
                System.err.println(e.getMessage());
            }

            Thread.sleep(1000);
        }
    }
}
