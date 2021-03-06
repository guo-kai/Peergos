package peergos.server.corenode;

import peergos.server.storage.admin.*;
import peergos.shared.corenode.*;
import peergos.shared.crypto.*;
import peergos.shared.crypto.hash.*;
import peergos.shared.io.ipfs.multihash.*;
import peergos.shared.util.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class SignUpFilter implements CoreNode {

    private final CoreNode target;
    private final QuotaAdmin judge;
    private final Multihash ourNodeId;

    public SignUpFilter(CoreNode target, QuotaAdmin judge, Multihash ourNodeId) {
        this.target = target;
        this.judge = judge;
        this.ourNodeId = ourNodeId;
    }

    @Override
    public CompletableFuture<List<UserPublicKeyLink>> getChain(String username) {
        return target.getChain(username);
    }

    @Override
    public CompletableFuture<Optional<RequiredDifficulty>> updateChain(String username,
                                                                       List<UserPublicKeyLink> chain,
                                                                       ProofOfWork proof,
                                                                       String token) {
        boolean forUs = chain.get(chain.size() - 1).claim.storageProviders.contains(ourNodeId);
        if (! forUs)
            return target.updateChain(username, chain, proof, token);

        if (judge.allowSignupOrUpdate(username, token)) {
            return target.updateChain(username, chain, proof, token).thenApply(res -> {
                if (res.isEmpty())
                    judge.consumeToken(username, token);
                return res;
            });
        }
        if (! token.isEmpty())
            return Futures.errored(new IllegalStateException("Invalid signup token."));

        return Futures.errored(new IllegalStateException("This server is not currently accepting new sign ups. Please try again later"));
    }

    @Override
    public CompletableFuture<String> getUsername(PublicKeyHash key) {
        return target.getUsername(key);
    }

    @Override
    public CompletableFuture<List<String>> getUsernames(String prefix) {
        return target.getUsernames(prefix);
    }

    @Override
    public void close() throws IOException {
        target.close();
    }
}
