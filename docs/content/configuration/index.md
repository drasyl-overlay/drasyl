# Configuration

drasyl is designed to work with zero-configuration.
However, some use cases (e.g. using an own super peer) require customization configurations.
For this situation there are various parameters available to adjust the behavior of drasyl nodes.

An overview of all available parameters, their purpose and default values can be found in the [reference.conf](https://github.com/drasyl-overlay/drasyl/blob/master/drasyl-core/src/main/resources/reference.conf) file.

## Create custom configurations

Because drasyl's configuration is based on [Lightbend Config library](https://github.com/lightbend/config), there are many ways to create custom configurations.

### Use `DrasylConfig.Buidler`

With the [`DrasylConfig.Buidler`](https://www.javadoc.io/doc/org.drasyl/drasyl-core/latest/org/drasyl/DrasylConfig.Builder.html) class, configurations can be created within Java.
This allows you to define individual configurations for each node.
It is done by calling [`DrasylConfig.newBuilder() ... .build()`](https://www.javadoc.io/doc/org.drasyl/drasyl-core/latest/org/drasyl/DrasylConfig.html#newBuilder()).
Available builder methods can be obtained from the [Javadoc](https://www.javadoc.io/doc/org.drasyl/drasyl-core/latest/org/drasyl/DrasylConfig.Builder.html).

Example:
```java
DrasylConfig config = DrasylConfig.newBuilder()
    .identityPath(Path.of("/Users/heiko/drasyl.identity.json"))
    .networkId(-25421)
    .remoteSuperPeerEndpoint(Endpoint.of("udp://staging.env.drasyl.org#Awlq4wgKNpgppEhH1a8fZSvvP5kh6eG7rWSXC6vm08UC"))
    .remoteEnabled(false)
    .build();
```

The resulting [`DrasylConfig`](https://www.javadoc.io/doc/org.drasyl/drasyl-core/latest/org/drasyl/DrasylConfig.html) object can now be passed to the [`DrasylNode` constructor](https://www.javadoc.io/doc/org.drasyl/drasyl-core/latest/org/drasyl/DrasylNode.html#%3Cinit%3E(org.drasyl.DrasylConfig)).

### Use `application.conf` file

You can add a resource called `application.conf` to your java classpath to provide a custom config used by all your nodes.
This file has to use the [HOCON Syntax](https://github.com/lightbend/config/blob/master/HOCON.md).
The file only needs to contain the parameters you want to overwrite because it will be merged with the default values found in [reference.conf](https://github.com/drasyl-overlay/drasyl/blob/master/drasyl-core/src/main/resources/reference.conf).

Example:
```hocon
drasyl.identity.path = /Users/heiko/drasyl.identity.json
drasyl.network.id = -25421
drasyl.remote.super-peer.endpoint = "udp://staging.env.drasyl.org#Awlq4wgKNpgppEhH1a8fZSvvP5kh6eG7rWSXC6vm08UC"
```

### Use environment variables

By setting the JVM property `-Dconfig.override_with_env_vars=true` it is possible to override any configuration value using environment variables.

With this option enabled only environment variables starting with `CONFIG_FORCE_` are considered, and the name is mangled as follows:

* the prefix `CONFIG_FORCE_` is stripped
* single underscore(`_`) is converted into a dot(`.`)
* double underscore(`__`) is converted into a dash(`-`)
* triple underscore(`___`) is converted into a single underscore(`_`)

i.e. The environment variable `CONFIG_FORCE_a_b__c___d` set the configuration key `a.b-c_d`

Example:
```bash
$ CONFIG_FORCE_drasyl_identity_path=/Users/heiko/drasyl.identity.json \
    CONFIG_FORCE_drasyl_network_id=-25421 \
    CONFIG_FORCE_drasyl_remote_super__peer_endpoint=udp://staging.env.drasyl.org#Awlq4wgKNpgppEhH1a8fZSvvP5kh6eG7rWSXC6vm08UC \
    your-application.jar
```

!!! info "Advanced References"

    Further information regarding formatting can be taken directly from the configuration library that is used internally by drasyl: [https://github.com/lightbend/config](https://github.com/lightbend/config)
