package org.drasyl.identity;

import net.javacrumbs.jsonunit.core.Option;
import org.drasyl.DrasylConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class IdentityManagerIT {
    @Mock
    private DrasylConfig config;

    @Test
    void shouldThrowExceptionIfConfigContainsNoKeysAndPathDoesNotExist(@TempDir Path dir) {
        Path path = Paths.get(dir.toString(), "non-existing", "my-identity.json");
        when(config.getIdentityPath()).thenReturn(path);

        IdentityManager identityManager = new IdentityManager(config);

        assertThrows(IdentityManagerException.class, identityManager::loadOrCreateIdentity);
    }
}
