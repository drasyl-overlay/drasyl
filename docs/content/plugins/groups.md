# Groups

With the **groups** Plugins nodes can organize themselves in groups. Members within the
group will be automatically notified about the entry and exit of other nodes.

## Client

The **groups-client** plugin enables nodes to join groups.

### Add Dependency

Maven:
```xml
<dependency>
    <groupId>org.drasyl</groupId>
    <artifactId>drasyl-plugin-groups-client</artifactId>
    <version>0.5.0-SNAPSHOT</version>
</dependency>
```

Other dependency managers:
```java
Gradle : compile "org.drasyl:drasyl-plugin-groups-client:0.5.0-SNAPSHOT" // build.gradle 
   Ivy : <dependency org="org.drasyl" name="drasyl-plugin-groups-client" rev="0.5.0-SNAPSHOT" conf="build" /> // ivy.xml
   SBT : libraryDependencies += "org.drasyl" % "drasyl-plugin-groups-client" % "0.5.0-SNAPSHOT" // build.sbt
```

### Configuration

Make sure that the following entry is included in your configuration under `drasyl.plugins`:

```hocon
"org.drasyl.plugin.groups.client.GroupsClientPlugin" {
  enabled = true
  groups = [
    "groups://my-shared-secret@023d34f317616c3bb0fa1e4b425e9419d1704ef57f6e53afe9790e00998134f5ff/steezy-vips?timeout=60"
  ]
}
```

With this configuration, the client will connect to the Groups Manager on the node
`023d34f317616c3bb0fa1e4b425e9419d1704ef57f6e53afe9790e00998134f5ff` at startup and will join the
group `steezy-vips`. Authentication is done via the shared secret `my-shared-secret`.

Special [Group Events](https://www.javadoc.io/doc/org.drasyl/drasyl-plugin-groups-client/latest/org/drasyl/plugin/groups/client/event/package-summary.html) will then inform you about group joins of your local or other nodes.

In the next section you will learn how to start the Group Manager on a node.

## Manager

The **groups-manager** allows a node to manage groups and their memberships.

### Add Dependency

Maven:
```xml
<dependency>
    <groupId>org.drasyl</groupId>
    <artifactId>drasyl-plugin-groups-manager</artifactId>
    <version>0.5.0-SNAPSHOT</version>
</dependency>
```

Other dependency managers:
```java
Gradle : compile "org.drasyl:drasyl-plugin-groups-manager:0.5.0-SNAPSHOT" // build.gradle 
   Ivy : <dependency org="org.drasyl" name="drasyl-plugin-groups-manager" rev="0.5.0-SNAPSHOT" conf="build" /> // ivy.xml
   SBT : libraryDependencies += "org.drasyl" % "drasyl-plugin-groups-manager" % "0.5.0-SNAPSHOT" // build.sbt
```

### Configuration

Make sure that the following entry is included in your configuration under `drasyl.plugins`:

```hocon
"org.drasyl.plugin.groups.manager.GroupsManagerPlugin" {
  enabled = true
  database {
    uri = "jdbc:sqlite::memory:"
  }
  groups {
    "steezy-vips" {
      secret = "my-shared-secret"
    }
  }
}
```

With this configuration the manager is created with the group `steezy-vips`, whose members must
authenticate themselves using the shared secret `my-shared-secret`. 
The manager stores all groups, nodes and their memberships in memory. To persistent the data on
file system, `database.uri` can be set to `jdbc:sqlite:groups-manager.sqlite`.

An overview of all available parameters (e.g. to enable the REST API), their purpose and default values can be found in the
plugin's [reference.conf](https://github.com/drasyl-overlay/drasyl/blob/master/drasyl-plugin-groups-manager/src/main/resources/reference.conf)
file.
