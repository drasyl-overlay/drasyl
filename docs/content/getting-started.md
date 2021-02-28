# Getting Started

!!! important "Nightly Version"

    You're currently on a nightly version branch. If you're only interested  in the latest stable version, please click [here](/).

This guide describes the necessary steps to create your first drasyl node and how to integrate it into your application.

Once the node is set up, it and therefore your application can participate in the drasyl Overlay Network and communicate with other nodes and applications.

## Add Dependency

Create a new maven project and add the dependency to your [pom.xml](http://maven.apache.org/pom.html):

Maven:
```xml
<repositories>
    <repository>
        <id>oss.sonatype.org-snapshot</id>
        <url>https://oss.sonatype.org/content/repositories/snapshots/</url>
        <releases>
            <enabled>false</enabled>
        </releases>
        <snapshots>
            <enabled>true</enabled>
        </snapshots>
    </repository>
</repositories>
```

```xml
<dependency>
    <groupId>org.drasyl</groupId>
    <artifactId>drasyl-core</artifactId>
    <version>0.5.0-SNAPSHOT</version>
</dependency>
```

Other dependency managers:
```java
Gradle : compile "org.drasyl:drasyl-core:0.5.0-SNAPSHOT" // build.gradle 
   Ivy : <dependency org="org.drasyl" name="drasyl-core" rev="0.5.0-SNAPSHOT" conf="build" /> // ivy.xml
   SBT : libraryDependencies += "org.drasyl" % "drasyl-core" % "0.5.0-SNAPSHOT" // build.sbt
```

## Implementing `DrasylNode`

Next, you can create your own drasyl node by implementing [`DrasylNode`](https://www.javadoc.io/doc/org.drasyl/drasyl-core/latest/org/drasyl/DrasylNode.html).

This class contains the following methods that are now relevant for you:

* [`send(...)`](https://www.javadoc.io/doc/org.drasyl/drasyl-core/latest/org/drasyl/DrasylNode.html#send(java.lang.String,java.lang.Object)): allows your application to send arbitrary messages to other drasyl nodes.
* [`onEvent(...)`](https://www.javadoc.io/doc/org.drasyl/drasyl-core/latest/org/drasyl/DrasylNode.html#onEvent(org.drasyl.event.Event)): allows your application to react to certain events (e.g. process received messages, connection to the network established/lost). This method must be implemented.
* [`start()`](https://www.javadoc.io/doc/org.drasyl/drasyl-core/latest/org/drasyl/DrasylNode.html#start()): starts the node, which will then automatically connect to the drasyl network.
* [`shutdown()`](https://www.javadoc.io/doc/org.drasyl/drasyl-core/latest/org/drasyl/DrasylNode.html#shutdown()): disconnects from the drasyl network and shuts down the node.
 
Here you can see a minimal working example of a node that forwards all received events to `System.out`:
```java
DrasylNode node = new DrasylNode() {
    @Override
    public void onEvent(Event event) {
        System.out.println("Event received: " + event);
    }
};
```

## Node Events

As mentioned before, different events are received by the application.
These provide information about the state of your node, received messages or connections to other nodes.
It is therefore important that you become familiar with the [definitions and implications](https://www.javadoc.io/doc/org.drasyl/drasyl-core/latest/org/drasyl/event/package-summary.html) of
the different event types.

For example, you should listen for `NodeOnlineEvent` before start sending messages, and pause when `NodeOfflineEvent` has been received.

!!! info "Advanced References"
    
    If you are interested in the life cycle of the individual events, you can find a state diagram [here](../architecture/diagrams/#node-events).

## Sending Messages

Every message that is to be sent requires a recipient address.
Each drasyl node creates an identity at its first startup consisting of a cryptographic public-private key pair.
From the public key, a 10 hex digit address is derived, by which each node can be uniquely identified.

The `send()` method needs the recipient as first argument and the message payload as second argument.

Example:
```java
CompletableFuture<Void> result = node.send("0229041b273dd5ee1c2bef2d77ae17dbd00d2f0a2e939e22d42ef1c4bf05147ea9", "Hello World".getBytes());
```

The returned [Future](https://www.baeldung.com/java-completablefuture) does not give any feedback on whether the message could be delivered. 
The future is successfully completed if the local node could deliver the message itself or forward it to a super peer that acts as a default gateway.
Otherwise the future is completed exceptionally.

!!! info "Advanced References"
    
    The addresses of recipient nodes must be known, as drasyl-core has no function for querying available addresses.
    The [Groups Plugin](plugins/groups.md) can be used to get automatically notified about other nodes joining and leaving the network.

## Receiving Messages

Each received message is announced by an [`MessageEvent`](https://www.javadoc.io/doc/org.drasyl/drasyl-core/latest/org/drasyl/event/MessageEvent.html) to the application.
The event contains a getters for the message's sender and payload.

Example:
```java
...
public void onEvent(Event event) {
    if (event instanceof MessageEvent) {
        MessageEvent message = (MessageEvent) event;
        System.out.println("Message received from " + message.getSender() + " with payload " + new String(message.getPayload()));
    }
}
...
```

## Starting & Stopping the drasyl Node

Before you can use the drasyl node, you must start it using [`node.start()`](https://www.javadoc.io/doc/org.drasyl/drasyl-core/latest/org/drasyl/DrasylNode.html#start()).
For communication with other nodes in the local network, the node starts a server
listening on port 22527. Make sure that the port is available.
After the node has been successfully started, it emits an [`NodeUpEvent`](https://www.javadoc.io/doc/org.drasyl/drasyl-core/latest/org/drasyl/event/NodeUpEvent.html) to the application.
Then, once it has successfully connected to the overlay network, an [`NodeOnlineEvent`](https://www.javadoc.io/doc/org.drasyl/drasyl-core/latest/org/drasyl/event/NodeOnlineEvent.html) is emitted.

If the node is temporarily or permanently no longer needed, it can be shut down using [`node.shutdown()`](https://www.javadoc.io/doc/org.drasyl/drasyl-core/latest/org/drasyl/DrasylNode.html#shutdown()).
A [`NodeDownEvent`](https://www.javadoc.io/doc/org.drasyl/drasyl-core/latest/org/drasyl/event/NodeDownEvent.html) is emitted immediately after this call. The application should now no longer attempt to send messages.
As soon as the connection to the drasyl network is terminated, an [`NodeOfflineEvent`](https://www.javadoc.io/doc/org.drasyl/drasyl-core/latest/org/drasyl/event/NodeOfflineEvent.html) is emitted.
A [`NodeNormalTerminationEvent`](https://www.javadoc.io/doc/org.drasyl/drasyl-core/latest/org/drasyl/event/NodeNormalTerminationEvent.html) is emitted when the shutdown is done.


## Summary

In this chapter, we had a quick tour of drasyl with a demonstration on how to create a node, start and stop it, send messages, and process emitted events.

Have a look at [our examples](https://github.com/drasyl-overlay/drasyl/tree/master/drasyl-examples/src/main/java/org/drasyl/example) to see how drasyl can be used for different scenarios.
