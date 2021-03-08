/*
 * Copyright (c) 2020-2021.
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
package org.drasyl.plugin.groups.client.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.plugin.groups.client.Group;

import java.util.Objects;

import static java.util.Objects.requireNonNull;
import static org.drasyl.util.SecretUtil.maskSecret;

/**
 * This message is send by the groups client to the server to join a group.
 * <p>
 * This is an immutable object.
 */
public class GroupJoinMessage extends GroupActionMessage implements GroupsClientMessage {
    /*
     * FIXME: This secret is transmitted in plain text as long as
     * drasyl does not support end-to-end encryption for all messages.
     */
    private final String credentials;
    private final ProofOfWork proofOfWork;
    private final boolean renew;

    @JsonCreator
    public GroupJoinMessage(@JsonProperty("group") final Group group,
                            @JsonProperty("credentials") final String credentials,
                            @JsonProperty("proofOfWork") final ProofOfWork proofOfWork,
                            @JsonProperty("renew") final boolean renew) {
        super(group);
        this.credentials = requireNonNull(credentials);
        this.proofOfWork = requireNonNull(proofOfWork);
        this.renew = renew;
    }

    public boolean isRenew() {
        return renew;
    }

    public String getCredentials() {
        return credentials;
    }

    public ProofOfWork getProofOfWork() {
        return proofOfWork;
    }

    @Override
    public String toString() {
        return "GroupJoinMessage{" +
                "credentials='" + maskSecret(credentials) + '\'' +
                ", group='" + group + '\'' +
                ", proofOfWork=" + proofOfWork + '\'' +
                ", renew=" + renew +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final GroupJoinMessage that = (GroupJoinMessage) o;
        return Objects.equals(credentials, that.credentials) &&
                Objects.equals(proofOfWork, that.proofOfWork) &&
                Objects.equals(renew, that.renew);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), credentials, proofOfWork, renew);
    }
}
