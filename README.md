# [<img src="https://docs.drasyl.org/master/assets/img/logo.svg" alt="drasyl" width="200"/>](https://drasyl.org)

[Website](https://drasyl.org) |
[Documentation](https://docs.drasyl.org) |
[Contributing](CONTRIBUTING.md) |
[Changelog](CHANGELOG.md)

[![Build Status](https://git.informatik.uni-hamburg.de/sane-public/drasyl/badges/master/pipeline.svg)](https://git.informatik.uni-hamburg.de/sane-public/drasyl/-/pipelines)
[![LGPL v3](https://img.shields.io/badge/license-LGPL%20v3-blue)](https://www.gnu.org/licenses/lgpl-3.0)
[![Maven Central](https://img.shields.io/maven-central/v/org.drasyl/drasyl-core.svg)](https://mvnrepository.com/artifact/org.drasyl/drasyl-core)
[![Javadocs](https://javadoc.io/badge2/org.drasyl/drasyl-core/javadoc.svg)](https://www.javadoc.io/doc/org.drasyl/drasyl-core)
[![Docker Pulls](https://img.shields.io/docker/pulls/drasyl/drasyl)](https://hub.docker.com/r/drasyl/drasyl)
[![Chocolatey](https://img.shields.io/chocolatey/v/drasyl)](https://chocolatey.org/packages/drasyl)
[![Gitter](https://badges.gitter.im/drasyl-overlay/drasyl.svg)](https://gitter.im/drasyl-overlay/drasyl)

# drasyl

drasyl is a general-purpose overlay network framework for rapid development of distributed P2P
applications.

By using drasyl developers can fully concentrate on creating distributed applications. With drasyl,
boundaries between IP-based networks will be eliminated and secure communication channels between
any peers will be provided. Zero-configuration is required to use drasyl. Developers can run a new
drasyl node without having to write configuration files or provide IP addresses of peers.

![drasyl architecture](https://docs.drasyl.org/master/assets/img/drasyl-architecture.png)

_As drasyl is primarily developed for the research project
[Smart Networks for Urban Participation (SANE)](https://sane.city/) and focuses on functionalities
necessary for the project. However, drasyl is open to contributions made by the community._

# Features

* Provides Communication Channels between any two Nodes (on the Internet).
* Automatic Discovery of Peers running within same Process, Computer, LAN, or the Internet.
* Automatic Handover to most local Communication Channel.
* Overcomes Network Barriers (Firewalls, NATs).
* UDP Hole Punching.
* PortMapping (UPnP-IGD, NAT-PMP, PCP).  
* Adapts to Network Changes.
* Lightweight.
* Extensible.

# Usage & Documentation

* [Getting Started](https://docs.drasyl.org/getting-started/)
* [Configuration](https://docs.drasyl.org/configuration/)
* [JavaDoc](https://www.javadoc.io/doc/org.drasyl/drasyl-core/latest/index.html)  
* [Command Line Interface](https://docs.drasyl.org/cli/)
* [Chat](https://gitter.im/drasyl-overlay/drasyl)

# License

This is free software under the terms of the [GNU Lesser General Public License v3.0](LICENSE)
