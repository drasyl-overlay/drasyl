# drasyl

drasyl is an open source, general purpose overlay network that is concurrent, resilient, flexible, automated and presents itself to the user as a transparent
system which offers suitable discovery and awareness methods, particularly with a focus on smart city and IoT devices. Nevertheless, drasyl is not limited to
smart city and IoT, but is intended for universal use in all decentralized Java-based projects.

As this overlay network is primarily developed for the research project
[Smart Networks for Urban Participation (SANE)](https://sane.city/), it primarily covers the functionalities necessary for the project. However, we are open to
contributions.

drasyl was inspired by [ZeroTier's Peer to Peer Network](https://www.zerotier.com/manual/#2_1).

You can either include this implementation in your own software stack and make use of the overlay network as a transport medium, or use the
[command line interface](drasyl-cli) to run self hosted (root) super peer nodes.

## Requirements

* Java 11

## Installation

### Maven

Either build and install drasyl by yourself...
```bash
mvn install
```

...or pull it from public repo:

Add GitLab Maven Repository to `pom.xml`:
```xml
<repositories>
    <repository>
        <id>gitlab-maven</id>
        <url>https://git.informatik.uni-hamburg.de/api/v4/groups/sane-public/-/packages/maven</url>
    </repository>
</repositories>
```

Add drasyl as dependency to your `pom.xml`:
```xml
<dependency>
    <groupId>org.drasyl</groupId>
    <artifactId>drasyl-core</artifactId>
    <version>0.1.0</version>
</dependency>
```

### Official Builds

https://git.informatik.uni-hamburg.de/sane-public/drasyl/-/releases

### Usage

```java
// create and start node
DrasylNode node = new DrasylNode() {
    @Override
    public void onEvent(Event event) {
        // handle incoming events (messages) here
        System.out.println("Event received: " + event);
    }
};
node.start();

// wait till EVENT_NODE_ONLINE has been received

// send message to another node
node.send("0229041b273dd5ee1c2bef2d77ae17dbd00d2f0a2e939e22d42ef1c4bf05147ea9", "Hello World");

// shutdown node
node.shutdown();
```

### Documentation

More information can be found in the (still very short) [documentation](doc/README.md).

## License

This project is licensed under the [GNU Lesser General Public License v3.0](LICENSE).