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
    <version>0.3.0-SNAPSHOT</version>
</dependency>
```

Gradle:

```gradle
compile group: 'org.drasyl', name: 'drasyl-plugin-groups-client', version: '0.3.0-SNAPSHOT'
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

Special [Group Events](../../drasyl-plugin-groups-client/src/main/java/org/drasyl/plugin/groups/client/event) will then inform you about group joins of your local or other nodes.

In the next section you will learn how to start the Group Manager on a node.

## Manager

The **groups-manager** allows a node to manage groups and their memberships.

### Add Dependency

Maven:
```xml
<dependency>
    <groupId>org.drasyl</groupId>
    <artifactId>drasyl-plugin-groups-manager</artifactId>
    <version>0.3.0-SNAPSHOT</version>
</dependency>
```

Gradle:

```gradle
compile group: 'org.drasyl', name: 'drasyl-plugin-groups-manager', version: '0.3.0-SNAPSHOT'
```

### Configuration

Make sure that the following entry is included in your configuration under `drasyl.plugins`:

```hocon
"org.drasyl.plugin.groups.manager.GroupsManagerPlugin" {
  enabled = true
  database {
    uri: "jdbc:sqlite::memory:"
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