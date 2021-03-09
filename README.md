# <img src="https://docs.drasyl.org/master/assets/img/logo.svg" alt="drasyl" width="200"/>

[![Build Status](https://git.informatik.uni-hamburg.de/sane-public/drasyl/badges/master/pipeline.svg)](https://git.informatik.uni-hamburg.de/sane-public/drasyl/-/pipelines)
[![LGPL v3](https://img.shields.io/badge/license-LGPL%20v3-blue)](https://www.gnu.org/licenses/lgpl-3.0)
[![Maven Central](https://img.shields.io/maven-central/v/org.drasyl/drasyl-core.svg)](https://mvnrepository.com/artifact/org.drasyl/drasyl-core)
[![Javadocs](https://javadoc.io/badge2/org.drasyl/drasyl-core/javadoc.svg)](https://www.javadoc.io/doc/org.drasyl/drasyl-core)
[![Docker Pulls](https://img.shields.io/docker/pulls/drasyl/drasyl)](https://hub.docker.com/r/drasyl/drasyl)
[![Chocolatey](https://img.shields.io/chocolatey/v/drasyl)](https://chocolatey.org/packages/drasyl)
[![Gitter](https://badges.gitter.im/drasyl-overlay/drasyl.svg)](https://gitter.im/drasyl-overlay/drasyl)

drasyl is a general-purpose overlay network framework for rapid development of distributed P2P applications.

By using drasyl developers can fully concentrate on creating distributed applications.
With drasyl, boundaries between IP-based networks will be eliminated and secure communication channels between any peers will be provided.
Zero-configuration is required to use drasyl.
Developers can run a new drasyl node without having to write configuration files or provide IP addresses of peers.

![drasyl architecture](https://docs.drasyl.org/master/assets/img/drasyl-architecture.png)

_As drasyl is primarily developed for the research project
[Smart Networks for Urban Participation (SANE)](https://sane.city/) and focuses on functionalities necessary for the project. However, drasyl is open to
contributions made by the community._

## Maven/Gradle Dependencies

Maven:
```xml
<dependency>
    <groupId>org.drasyl</groupId>
    <artifactId>drasyl-core</artifactId>
    <version>0.3.0</version>
</dependency>
```

Gradle:

```compile group: 'org.drasyl', name: 'drasyl-core', version: '0.3.0'```

## Standalone Command Line Interface

There is a drasyl command line interface with some utilities that can be found on the releases page: https://github.com/drasyl-overlay/drasyl/releases

It is also available in docker:

```docker run drasyl/drasyl help```

## Create and Start drasyl Node

```java
DrasylNode node = new DrasylNode() {
    @Override
    public void onEvent(Event event) {
        // handle incoming events (messages) here
        System.out.println("Event received: " + event);
    }
};
node.start();

// wait till NodeOnlineEvent has been received

// send message to another node
node.send("0229041b273dd5ee1c2bef2d77ae17dbd00d2f0a2e939e22d42ef1c4bf05147ea9", "Hello World");

// shutdown node
node.shutdown();
```

## Documentation

More information can be found in the [documentation](https://docs.drasyl.org).

-------------------------------------
_Licensed under [GNU Lesser General Public License v3.0](LICENSE)_

_Inspired by [ZeroTier's Peer to Peer Network](https://www.zerotier.com/manual/#2_1)_
